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
        final File dictDir = getDictionaryDir(context);
        final File dictFile = new File(dictDir, "dict_" + lang + ".bin");
        if (!dictFile.exists()) {
            // Crash recovery: if target was renamed to .bak and process died before staging was renamed
            final File backupFile = new File(dictDir, "dict_" + lang + ".bin.bak");
            if (backupFile.exists() && backupFile.length() > 16) {
                backupFile.renameTo(dictFile);
            }
        }
        if (dictFile.exists() && dictFile.length() > 16) {
            return dictFile;
        }
        return null;
    }

    public File getTransformerModelFile(final Context context, final String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) {
            return null;
        }
        final String lang = languageCode.toLowerCase();
        final File dictDir = getDictionaryDir(context);
        // Buscar transformer primero, luego neural
        File modelFile = new File(dictDir, "transformer_" + lang + ".bin");
        if (modelFile.exists() && modelFile.length() > 64) {
            return modelFile;
        }
        modelFile = new File(dictDir, "neural_" + lang + ".bin");
        if (modelFile.exists() && modelFile.length() > 64) {
            return modelFile;
        }
        return null;
    }

    public List<CustomDictInfo> getInstalledDictionaries(final Context context) {
        final File dir = getDictionaryDir(context);
        // Recovery pass for any orphaned .bak files where target .bin was lost in crash window
        final File[] bakFiles = dir.listFiles((d, name) -> name.startsWith("dict_") && name.endsWith(".bin.bak"));
        if (bakFiles != null) {
            for (final File bak : bakFiles) {
                final String targetName = bak.getName().substring(0, bak.getName().length() - ".bak".length());
                final File target = new File(dir, targetName);
                if (!target.exists() && bak.length() > 16) {
                    bak.renameTo(target);
                } else if (target.exists()) {
                    bak.delete(); // Target exists, clean stale backup
                }
            }
        }

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
        String name = fileName.trim();
        try {
            name = java.net.URLDecoder.decode(name, "UTF-8");
        } catch (Exception ignored) {
        }
        if (name == null || name.isEmpty()) {
            name = fileName.trim();
        }
        // Normalize path separators and remove directory/URI prefix
        final int lastColon = name.lastIndexOf(':');
        final int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        final int lastSep = Math.max(lastColon, lastSlash);
        if (lastSep >= 0 && lastSep < name.length() - 1) {
            name = name.substring(lastSep + 1);
        }

        name = name.trim().toLowerCase(java.util.Locale.US);

        // Strip known trailing download / duplicate suffixes: e.g. " (1)", "(2)"
        name = name.replaceAll("\\s*\\(\\d+\\)", "");

        // Strip trailing extensions (.bin, .dict, .gz, .combined, .tmp, .txt)
        while (true) {
            final int dotIdx = name.lastIndexOf('.');
            if (dotIdx > 0) {
                final String ext = name.substring(dotIdx);
                if (ext.equals(".bin") || ext.equals(".dict") || ext.equals(".gz")
                        || ext.equals(".combined") || ext.equals(".tmp") || ext.equals(".txt")) {
                    name = name.substring(0, dotIdx);
                    continue;
                }
            }
            break;
        }

        // Strip trailing version/number suffixes like _1, -1, -2 if present
        name = name.replaceAll("[-_]\\d+$", "");

        // Strip common prefixes
        final String[] prefixes = {
                "simple-keyboard-dict-",
                "simple-keyboard-dict_",
                "simple_keyboard_dict_",
                "dict_",
                "dict-",
                "transformer_",
                "transformer-",
                "neural_",
                "neural-",
                "main_",
                "main-",
                "extra_",
                "extra-",
                "dictionary_",
                "dictionary-",
                "wordlist_",
                "wordlist-"
        };
        for (final String p : prefixes) {
            if (name.startsWith(p)) {
                name = name.substring(p.length());
                break;
            }
        }

        // Match exact language tags like "es", "en", "es_es", "es-419", "en_us", "pt_br", "zh_hans", "sr_latn"
        final java.util.regex.Pattern langTagPattern = java.util.regex.Pattern.compile("^([a-z]{2,3})(?:[-_][a-z0-9]{2,4})?$");
        final java.util.regex.Matcher langTagMatcher = langTagPattern.matcher(name);
        if (langTagMatcher.matches()) {
            final String baseLang = langTagMatcher.group(1);
            if (isValidLanguageCode(baseLang)) {
                return baseLang;
            }
        }

        // Split by delimiter (_, -, .) and check individual tokens
        final String[] parts = name.split("[-_.]");
        for (final String part : parts) {
            if (isValidLanguageCode(part)) {
                return part;
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
                    final int nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
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
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                if (is == null) {
                    return new ImportResult(false, null, 0, "Could not open file URI");
                }
                copyStreamWithGzipDetection(is, tempFile);
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
                copyFileWithSync(tempFile, stagingFile);

                int wordCount = 0;
                try (FileInputStream fis = new FileInputStream(stagingFile);
                     FileChannel channel = fis.getChannel()) {
                    final ByteBuffer buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, stagingFile.length());
                    final BinaryTrieDictionary dict = new BinaryTrieDictionary(buf);
                    if (!dict.validateStructure()) {
                        return new ImportResult(false, null, 0, "Corrupt SKDB dictionary structure or node bounds");
                    }
                    wordCount = dict.getWordCount();
                }

                publishStagedFile(stagingFile, targetFile);

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
                            "Could not determine language for dictionary file (expected name like dict_<lang>.dict)");
                }

                final File targetFile = new File(dictDir, "dict_" + targetLang + ".bin");
                stagingFile = File.createTempFile("dict_stage_", ".bin", dictDir);
                BinaryTrieCompiler.compile(decoded.words, decoded.bigrams, stagingFile);

                try (FileInputStream fis = new FileInputStream(stagingFile);
                     FileChannel channel = fis.getChannel()) {
                    final ByteBuffer buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, stagingFile.length());
                    final BinaryTrieDictionary dict = new BinaryTrieDictionary(buf);
                    if (!dict.validateStructure()) {
                        return new ImportResult(false, null, 0, "Corrupt compiled dictionary structure");
                    }
                }

                publishStagedFile(stagingFile, targetFile);

                return new ImportResult(true, targetLang, decoded.words.size(),
                        "Successfully imported and compiled AOSP .dict (" + decoded.words.size() + " words)");
            }

            // Case 3: TRF1 Micro-Transformer model
            if (magic == 0x54524631 || magic == 0x31465254) {
                return importRawBinaryModel(tempFile, dictDir, detectedLang, fallbackLang, "transformer_", "Micro-Transformer model");
            }

            // Case 4: NLM1 Neural model
            if (magic == 0x4E4C4D31 || magic == 0x314D4C4E) {
                return importRawBinaryModel(tempFile, dictDir, detectedLang, fallbackLang, "neural_", "neural language model");
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

    private static final long MAX_DECOMPRESSED_BYTES = 50L * 1024L * 1024L; // 50 MB hard limit

        private static void copyStreamWithSyncAndLimit(java.io.InputStream in, java.io.FileOutputStream out, long maxBytes) throws IOException {
        final byte[] buffer = new byte[16384];
        long totalRead = 0;
        int len;
        while ((len = in.read(buffer)) != -1) {
            totalRead += len;
            if (maxBytes > 0 && totalRead > maxBytes) {
                throw new IOException("Dictionary exceeds maximum allowed size (" + (maxBytes / (1024 * 1024)) + " MB)");
            }
            out.write(buffer, 0, len);
        }
        out.flush();
        out.getFD().sync();
    }

    private static void copyStreamWithGzipDetection(final InputStream rawIn, final File dst) throws IOException {
        final File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        final java.io.PushbackInputStream pbis = new java.io.PushbackInputStream(rawIn, 2);
        final byte[] header = new byte[2];
        final int read = pbis.read(header);
        InputStream effectiveIn = pbis;
        if (read == 2 && header[0] == (byte) 0x1F && header[1] == (byte) 0x8B) {
            pbis.unread(header);
            effectiveIn = new java.util.zip.GZIPInputStream(pbis);
        } else if (read > 0) {
            pbis.unread(header, 0, read);
        }

        try (FileOutputStream fos = new FileOutputStream(dst)) {
            copyStreamWithSyncAndLimit(effectiveIn, fos, MAX_DECOMPRESSED_BYTES);
        }
    }

    private static void copyFileWithSync(final File src, final File dst) throws IOException {
        final File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            copyStreamWithSyncAndLimit(in, out, -1);
        }
    }

    private static ImportResult importRawBinaryModel(final File tempFile, final File dictDir,
            final String detectedLang, final String fallbackLang,
            final String filePrefix, final String modelType) throws IOException {
        final String targetLang = (detectedLang != null)
                ? detectedLang
                : (isValidLanguageCode(fallbackLang) ? fallbackLang.toLowerCase(java.util.Locale.US) : null);
        if (targetLang == null) {
            return new ImportResult(false, null, 0,
                    "Could not determine language for " + modelType + " (expected name like " + filePrefix + "<lang>.bin)");
        }
        final File targetFile = new File(dictDir, filePrefix + targetLang + ".bin");
        final File stagingFile = File.createTempFile(filePrefix + "stage_", ".bin", dictDir);
        try {
            copyFileWithSync(tempFile, stagingFile);
            publishStagedFile(stagingFile, targetFile);
            return new ImportResult(true, targetLang, 0, "Successfully imported " + modelType);
        } finally {
            if (stagingFile.exists()) {
                stagingFile.delete();
            }
        }
    }

    public static void publishStagedFile(final File stagingFile, final File targetFile) throws IOException {
        if (!stagingFile.exists()) {
            throw new IOException("Staging file does not exist: " + stagingFile.getAbsolutePath());
        }
        final File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.nio.file.Files.move(
                    stagingFile.toPath(),
                    targetFile.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
        } else {
            // Atomic rename on same POSIX filesystem
            if (!stagingFile.renameTo(targetFile)) {
                // If renameTo fails because target exists on some OS variants:
                // Safely backup existing target before rename, and restore if second rename fails
                final File backupFile = new File(parent, targetFile.getName() + ".bak");
                if (targetFile.exists() && !targetFile.renameTo(backupFile)) {
                    throw new IOException("Could not backup existing dictionary for atomic replacement");
                }
                if (!stagingFile.renameTo(targetFile)) {
                    if (backupFile.exists()) {
                        backupFile.renameTo(targetFile); // Restore original target intact
                    }
                    throw new IOException("Atomic rename of staging dictionary failed");
                }
                if (backupFile.exists()) {
                    backupFile.delete();
                }
            }
        }
    }
}
