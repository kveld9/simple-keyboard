package rkr.simplekeyboard.inputmethod.latin.dict;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rkr.simplekeyboard.inputmethod.latin.dict.aosp.AospDictDecoder;
import rkr.simplekeyboard.inputmethod.latin.dict.binary.BinaryTrieCompiler;
import rkr.simplekeyboard.inputmethod.latin.dict.binary.BinaryTrieDictionary;

public final class CustomDictionaryManager {
    private static final String TAG = "CustomDictionaryManager";
    private static final String DICT_DIR_NAME = "custom_dictionaries";

    public static final class CustomDictInfo {
        public final String languageCode;
        public final String fileName;
        public final long fileSizeBytes;
        public final int wordCount;
        public final long lastModified;

        public CustomDictInfo(final String languageCode, final String fileName,
                              final long fileSizeBytes, final int wordCount, final long lastModified) {
            this.languageCode = languageCode;
            this.fileName = fileName;
            this.fileSizeBytes = fileSizeBytes;
            this.wordCount = wordCount;
            this.lastModified = lastModified;
        }
    }

    public static final class ImportResult {
        public final boolean success;
        public final String languageCode;
        public final int wordCount;
        public final String message;

        public ImportResult(final boolean success, final String languageCode, final int wordCount, final String message) {
            this.success = success;
            this.languageCode = languageCode;
            this.wordCount = wordCount;
            this.message = message;
        }
    }

    private static volatile CustomDictionaryManager sInstance;

    private CustomDictionaryManager() {
    }

    public static CustomDictionaryManager getInstance() {
        if (sInstance == null) {
            synchronized (CustomDictionaryManager.class) {
                if (sInstance == null) {
                    sInstance = new CustomDictionaryManager();
                }
            }
        }
        return sInstance;
    }

    public File getDictionaryDir(final Context context) {
        final File dir = new File(context.getFilesDir(), DICT_DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public File getCustomDictionaryFile(final Context context, final String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) {
            return null;
        }
        final String lang = languageCode.toLowerCase();
        final File dictFile = new File(getDictionaryDir(context), "dict_" + lang + ".bin");
        if (dictFile.exists() && dictFile.length() > 16) {
            return dictFile;
        }
        return null;
    }

    public List<CustomDictInfo> getInstalledDictionaries(final Context context) {
        final File dir = getDictionaryDir(context);
        final File[] files = dir.listFiles((d, name) -> name.startsWith("dict_") && name.endsWith(".bin"));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        final List<CustomDictInfo> list = new ArrayList<>();
        for (final File f : files) {
            final String name = f.getName();
            final String lang = name.substring("dict_".length(), name.length() - ".bin".length());
            int wordCount = 0;
            try (FileInputStream fis = new FileInputStream(f);
                 FileChannel channel = fis.getChannel()) {
                final ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, Math.min(f.length(), 32));
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                if (buffer.getInt(0) == 0x42444B53) {
                    wordCount = buffer.getInt(8);
                }
            } catch (Exception ignored) {
            }
            list.add(new CustomDictInfo(lang, name, f.length(), wordCount, f.lastModified()));
        }
        return list;
    }

    public synchronized boolean deleteCustomDictionary(final Context context, final String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) {
            return false;
        }
        final File file = new File(getDictionaryDir(context), "dict_" + languageCode.toLowerCase() + ".bin");
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    public static boolean isValidLanguageCode(final String lang) {
        if (lang == null || (lang.length() != 2 && lang.length() != 3)) {
            return false;
        }
        for (String iso : java.util.Locale.getISOLanguages()) {
            if (iso.equalsIgnoreCase(lang)) {
                return true;
            }
        }
        return false;
    }

    public static String parseLanguageFromFilename(final String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }
        String name = fileName.trim().toLowerCase(java.util.Locale.US);
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash < name.length() - 1) {
            name = name.substring(lastSlash + 1);
        }
        final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(?:dict_)?([a-z]{2,3})(?:_[a-z]{2,4})?(?:\\.[a-z0-9]+)?$");
        final java.util.regex.Matcher matcher = pattern.matcher(name);
        if (matcher.matches()) {
            final String candidate = matcher.group(1);
            if (isValidLanguageCode(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public static String extractLanguageFromUri(final Context context, final Uri uri) {
        if (context == null || uri == null) {
            return null;
        }
        String fileName = null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIdx >= 0) {
                        fileName = cursor.getString(nameIdx);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (fileName == null || fileName.isEmpty()) {
            fileName = uri.getLastPathSegment();
        }
        return parseLanguageFromFilename(fileName);
    }

    public synchronized ImportResult importDictionary(final Context context, final Uri uri, final String fallbackLang) {
        if (context == null || uri == null) {
            return new ImportResult(false, null, 0, "Invalid arguments");
        }

        final String detectedLang = extractLanguageFromUri(context, uri);
        File tempFile = null;
        File stagingFile = null;
        try {
            tempFile = File.createTempFile("import_dict_", ".tmp", context.getCacheDir());
            try (InputStream is = context.getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(tempFile)) {
                if (is == null) {
                    return new ImportResult(false, null, 0, "Could not open file URI");
                }
                final byte[] buffer = new byte[16384];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }

            if (tempFile.length() < 16) {
                return new ImportResult(false, null, 0, "File is too small or corrupted");
            }

            final int magic;
            try (FileInputStream fis = new FileInputStream(tempFile);
                 FileChannel channel = fis.getChannel()) {
                final ByteBuffer headerBuf = ByteBuffer.allocate(4);
                channel.read(headerBuf);
                headerBuf.flip();
                magic = headerBuf.getInt();
            }

            final File dictDir = getDictionaryDir(context);

            // Case 1: Already SKDB format (0x42444B53 in little endian = 0x534B4442 in big endian)
            if (magic == 0x534B4442 || magic == 0x42444B53) {
                final String targetLang = (detectedLang != null)
                        ? detectedLang
                        : (isValidLanguageCode(fallbackLang) ? fallbackLang.toLowerCase(java.util.Locale.US) : null);

                if (targetLang == null) {
                    return new ImportResult(false, null, 0,
                            "Could not determine language for dictionary file (expected name like dict_<lang>.bin)");
                }

                final File targetFile = new File(dictDir, "dict_" + targetLang + ".bin");
                stagingFile = File.createTempFile("dict_stage_", ".bin", dictDir);
                copyFile(tempFile, stagingFile);

                int wordCount = 0;
                try (FileInputStream fis = new FileInputStream(stagingFile);
                     FileChannel channel = fis.getChannel()) {
                    final ByteBuffer buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, stagingFile.length());
                    final BinaryTrieDictionary dict = new BinaryTrieDictionary(buf);
                    wordCount = dict.getWordCount();
                }

                if (targetFile.exists()) {
                    targetFile.delete();
                }
                if (!stagingFile.renameTo(targetFile)) {
                    copyFile(stagingFile, targetFile);
                    stagingFile.delete();
                }

                return new ImportResult(true, targetLang, wordCount, "Successfully imported SKDB binary dictionary");
            }

            // Case 2: AOSP Format v2 / v4
            if (magic == AospDictDecoder.MAGIC_AOSP_V202
                    || magic == AospDictDecoder.MAGIC_AOSP_V2
                    || magic == AospDictDecoder.MAGIC_AOSP_V4) {

                final AospDictDecoder.DecodedDictionary decoded = AospDictDecoder.decode(tempFile);
                final String targetLang = (decoded.languageCode != null && isValidLanguageCode(decoded.languageCode))
                        ? decoded.languageCode.toLowerCase(java.util.Locale.US)
                        : ((detectedLang != null) ? detectedLang : (isValidLanguageCode(fallbackLang) ? fallbackLang.toLowerCase(java.util.Locale.US) : null));

                if (targetLang == null) {
                    return new ImportResult(false, null, 0,
                            "Could not determine language from .dict metadata or filename");
                }

                final File targetFile = new File(dictDir, "dict_" + targetLang + ".bin");
                stagingFile = File.createTempFile("dict_stage_", ".bin", dictDir);
                BinaryTrieCompiler.compile(decoded.words, stagingFile);

                if (targetFile.exists()) {
                    targetFile.delete();
                }
                if (!stagingFile.renameTo(targetFile)) {
                    copyFile(stagingFile, targetFile);
                    stagingFile.delete();
                }

                return new ImportResult(true, targetLang, decoded.words.size(),
                        "Successfully imported and compiled AOSP .dict (" + decoded.words.size() + " words)");
            }

            return new ImportResult(false, null, 0, "Unrecognized dictionary format (expected .dict or .bin)");

        } catch (Throwable e) {
            Log.e(TAG, "Failed to import dictionary from URI: " + uri, e);
            return new ImportResult(false, null, 0, "Error importing dictionary: " + e.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            if (stagingFile != null && stagingFile.exists()) {
                stagingFile.delete();
            }
        }
    }

    private static void copyFile(final File src, final File dst) throws IOException {
        final File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            final byte[] buf = new byte[16384];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }
}
