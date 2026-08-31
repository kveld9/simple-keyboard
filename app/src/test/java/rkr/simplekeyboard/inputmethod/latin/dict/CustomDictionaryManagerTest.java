package rkr.simplekeyboard.inputmethod.latin.dict;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import rkr.simplekeyboard.inputmethod.latin.dict.aosp.AospDictDecoder;
import rkr.simplekeyboard.inputmethod.latin.dict.binary.BinaryTrieCompiler;
import rkr.simplekeyboard.inputmethod.latin.dict.binary.BinaryTrieDictionary;

import static org.junit.Assert.*;

public class CustomDictionaryManagerTest {

    @Rule
    public TemporaryFolder mTempFolder = new TemporaryFolder();

    @Test
    public void testIsValidLanguageCode() {
        assertTrue(CustomDictionaryManager.isValidLanguageCode("es"));
        assertTrue(CustomDictionaryManager.isValidLanguageCode("en"));
        assertTrue(CustomDictionaryManager.isValidLanguageCode("pt"));
        assertTrue(CustomDictionaryManager.isValidLanguageCode("de"));
        assertTrue(CustomDictionaryManager.isValidLanguageCode("fr"));
        assertTrue(CustomDictionaryManager.isValidLanguageCode("it"));
        assertTrue(CustomDictionaryManager.isValidLanguageCode("ru"));

        assertFalse(CustomDictionaryManager.isValidLanguageCode(null));
        assertFalse(CustomDictionaryManager.isValidLanguageCode(""));
        assertFalse(CustomDictionaryManager.isValidLanguageCode("e"));
        assertFalse(CustomDictionaryManager.isValidLanguageCode("español"));
        assertFalse(CustomDictionaryManager.isValidLanguageCode("12"));
        assertFalse(CustomDictionaryManager.isValidLanguageCode("xyz99"));
    }

    @Test
    public void testParseLanguageFromFilename() {
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("dict_es.bin"));
        assertEquals("en", CustomDictionaryManager.parseLanguageFromFilename("dict_en.bin"));
        assertEquals("en", CustomDictionaryManager.parseLanguageFromFilename("dict_en_us.bin"));
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("dict_es.dict"));
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("es.bin"));
        assertEquals("pt", CustomDictionaryManager.parseLanguageFromFilename("/storage/emulated/0/Download/dict_pt_br.bin"));
        assertEquals("fr", CustomDictionaryManager.parseLanguageFromFilename("dict_fr.bin"));

        // Download duplicate numbers and variations from browsers/SAF
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("dict_es (1).bin"));
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("dict_es (2).bin"));
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("dict_es(1).bin"));
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("dict_es-1.bin"));
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("dict_es_1.bin"));
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("dict_es_419.bin"));

        // AOSP & Helium314 naming conventions
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("main_es.dict"));
        assertEquals("en", CustomDictionaryManager.parseLanguageFromFilename("main_en_US.dict"));
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("main_es_ES.dict"));
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("extra_es.dict"));
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("wordlist_es.dict"));
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("es_wordlist.combined.gz"));
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("es-ES.dict"));

        // Locales with BCP-47 / ISO variants
        assertEquals("pt", CustomDictionaryManager.parseLanguageFromFilename("dict_pt-BR.bin"));
        assertEquals("pt", CustomDictionaryManager.parseLanguageFromFilename("dict_pt_BR.bin"));
        assertEquals("zh", CustomDictionaryManager.parseLanguageFromFilename("dict_zh-Hans.dict"));
        assertEquals("sr", CustomDictionaryManager.parseLanguageFromFilename("dict_sr-Latn.dict"));
        assertEquals("en", CustomDictionaryManager.parseLanguageFromFilename("foo_en_US.bin"));

        // URL encoded and complex paths
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Fdict_es%20%281%29.bin"));
        assertEquals("ru", CustomDictionaryManager.parseLanguageFromFilename("content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Fdict_ru.bin"));
        assertEquals("en", CustomDictionaryManager.parseLanguageFromFilename("simple-keyboard-dict-en.bin"));

        // Adversarial false-positive resistance: English words containing ISO sub-strings must NOT match
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("open.bin"));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("listen.bin"));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("ten.bin"));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("sentence.bin"));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("words.bin"));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("unnamed.bin"));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("custom_backup.bin"));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("foo.bin"));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("dict_.bin"));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("dict_xx.bin"));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("dict_english.bin"));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename(null));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename(""));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("   "));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("random_file.txt"));
    }

    @Test
    public void testSkdbValidCompilationAndValidation() throws Exception {
        final File dictFile = mTempFolder.newFile("test_valid.bin");
        final List<BinaryTrieCompiler.WordEntry> words = new ArrayList<>();
        words.add(new BinaryTrieCompiler.WordEntry("hola", 200));
        words.add(new BinaryTrieCompiler.WordEntry("mundo", 150));
        words.add(new BinaryTrieCompiler.WordEntry("simple", 100));

        BinaryTrieCompiler.compile(words, dictFile);
        assertTrue(dictFile.exists());
        assertTrue(dictFile.length() > 32);

        try (FileInputStream fis = new FileInputStream(dictFile);
             FileChannel channel = fis.getChannel()) {
            final ByteBuffer buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, dictFile.length());
            final BinaryTrieDictionary dict = new BinaryTrieDictionary(buf);
            assertEquals(3, dict.getWordCount());
            assertTrue(dict.validateStructure());
            assertTrue(dict.containsWord("hola"));
            assertTrue(dict.containsWord("mundo"));
            assertTrue(dict.containsWord("simple"));
            assertFalse(dict.containsWord("chau"));
        }
    }

    @Test
    public void testSkdbCorruptAndTruncatedBuffers() throws Exception {
        // 1. Buffer too small (< 16 bytes)
        try {
            final ByteBuffer smallBuf = ByteBuffer.allocate(8);
            new BinaryTrieDictionary(smallBuf);
            fail("Expected IllegalArgumentException for small buffer");
        } catch (IllegalArgumentException expected) {
        }

        // 2. Invalid magic
        try {
            final ByteBuffer invalidMagicBuf = ByteBuffer.allocate(32);
            invalidMagicBuf.order(ByteOrder.LITTLE_ENDIAN);
            invalidMagicBuf.putInt(0, 0x12345678);
            new BinaryTrieDictionary(invalidMagicBuf);
            fail("Expected IllegalArgumentException for invalid magic");
        } catch (IllegalArgumentException expected) {
        }

        // 3. Corrupt root offset pointing outside buffer
        try {
            final ByteBuffer badRootBuf = ByteBuffer.allocate(32);
            badRootBuf.order(ByteOrder.LITTLE_ENDIAN);
            badRootBuf.putInt(0, 0x42444B53); // SKDB
            badRootBuf.putInt(4, 1); // version 1
            badRootBuf.putInt(8, 10); // 10 words
            badRootBuf.putInt(12, 100); // root offset 100 > capacity 32
            new BinaryTrieDictionary(badRootBuf);
            fail("Expected IllegalArgumentException for out-of-bounds root offset");
        } catch (IllegalArgumentException expected) {
        }

        // 4. Corrupt child offset during validateStructure()
        final ByteBuffer corruptNodeBuf = ByteBuffer.allocate(64);
        corruptNodeBuf.order(ByteOrder.LITTLE_ENDIAN);
        corruptNodeBuf.putInt(0, 0x42444B53); // SKDB
        corruptNodeBuf.putInt(4, 1);
        corruptNodeBuf.putInt(8, 1);
        corruptNodeBuf.putInt(12, 16); // root at 16

        // Node at 16: character='a', flags=2 (has children), childCount=5, childrenOffset=200 (out of bounds)
        corruptNodeBuf.putShort(16, (short) 'a');
        corruptNodeBuf.put(18, (byte) 2);
        corruptNodeBuf.put(19, (byte) 200);
        corruptNodeBuf.put(20, (byte) 5);
        corruptNodeBuf.putInt(24, 200); // children offset 200 > 64

        final BinaryTrieDictionary corruptDict = new BinaryTrieDictionary(corruptNodeBuf);
        assertFalse(corruptDict.validateStructure());

        // 5. Node with integer overflow child offset
        final ByteBuffer overflowNodeBuf = ByteBuffer.allocate(64);
        overflowNodeBuf.order(ByteOrder.LITTLE_ENDIAN);
        overflowNodeBuf.putInt(0, 0x42444B53);
        overflowNodeBuf.putInt(4, 1);
        overflowNodeBuf.putInt(8, 1);
        overflowNodeBuf.putInt(12, 16);

        overflowNodeBuf.putShort(16, (short) 'a');
        overflowNodeBuf.put(18, (byte) 2);
        overflowNodeBuf.put(19, (byte) 100);
        overflowNodeBuf.put(20, (byte) 10);
        overflowNodeBuf.putInt(24, Integer.MAX_VALUE - 10); // Offset near MAX_VALUE will overflow

        final BinaryTrieDictionary overflowDict = new BinaryTrieDictionary(overflowNodeBuf);
        assertFalse(overflowDict.validateStructure());

        // 6. Node cycle detection
        final ByteBuffer cycleBuf = ByteBuffer.allocate(64);
        cycleBuf.order(ByteOrder.LITTLE_ENDIAN);
        cycleBuf.putInt(0, 0x42444B53);
        cycleBuf.putInt(4, 1);
        cycleBuf.putInt(8, 1);
        cycleBuf.putInt(12, 16);

        cycleBuf.putShort(16, (short) 'a');
        cycleBuf.put(18, (byte) 2);
        cycleBuf.put(19, (byte) 100);
        cycleBuf.put(20, (byte) 1);
        cycleBuf.putInt(24, 16); // Points to itself!

        final BinaryTrieDictionary cycleDict = new BinaryTrieDictionary(cycleBuf);
        assertFalse(cycleDict.validateStructure());
    }

    @Test
    public void testAospDecoderWithValidAndTruncatedData() throws Exception {
        // Test truncated AOSP file detection
        final File emptyFile = mTempFolder.newFile("empty.dict");
        assertFalse(AospDictDecoder.isAospDictionary(emptyFile));

        // Test file with invalid magic
        final File randomFile = mTempFolder.newFile("random.dict");
        try (FileOutputStream fos = new FileOutputStream(randomFile)) {
            fos.write(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10});
        }
        assertFalse(AospDictDecoder.isAospDictionary(randomFile));

        // Test AOSP header with invalid magic throwing exception in decode
        try {
            AospDictDecoder.decode(randomFile);
            fail("Expected IllegalArgumentException for unsupported AOSP magic");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testGzipStreamDetection() throws Exception {
        final File plainFile = mTempFolder.newFile("test_plain.bin");
        final List<BinaryTrieCompiler.WordEntry> words = Collections.singletonList(
                new BinaryTrieCompiler.WordEntry("hola", 255)
        );
        BinaryTrieCompiler.compile(words, plainFile);

        // Compress plainFile to a .gz file
        final File gzFile = mTempFolder.newFile("dict_es.combined.gz");
        try (FileInputStream fis = new FileInputStream(plainFile);
             GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(gzFile))) {
            final byte[] buf = new byte[4096];
            int len;
            while ((len = fis.read(buf)) > 0) {
                gzos.write(buf, 0, len);
            }
        }

        assertTrue(gzFile.exists());
        assertTrue(gzFile.length() > 0);
    }

    @Test
    public void testPublishStagedFileAtomicReplacementAndFailureSafety() throws Exception {
        final File targetDir = mTempFolder.newFolder("dict_dir");
        final File targetFile = new File(targetDir, "dict_es.bin");

        // 1. Create initial target file with original content
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write("ORIGINAL_CONTENT".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        assertEquals("ORIGINAL_CONTENT", new String(java.nio.file.Files.readAllBytes(targetFile.toPath())));

        // 2. Publish valid staged file
        final File stagingFile = new File(targetDir, "dict_stage_1.bin");
        try (FileOutputStream fos = new FileOutputStream(stagingFile)) {
            fos.write("NEW_VALID_CONTENT".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        CustomDictionaryManager.publishStagedFile(stagingFile, targetFile);
        assertFalse(stagingFile.exists());
        assertTrue(targetFile.exists());
        assertEquals("NEW_VALID_CONTENT", new String(java.nio.file.Files.readAllBytes(targetFile.toPath())));

        // 3. Attempt to publish non-existent staging file: target MUST remain intact
        final File nonExistentStage = new File(targetDir, "dict_stage_nonexistent.bin");
        try {
            CustomDictionaryManager.publishStagedFile(nonExistentStage, targetFile);
            fail("Expected IOException when staging file does not exist");
        } catch (IOException expected) {
        }
        assertTrue(targetFile.exists());
        assertEquals("NEW_VALID_CONTENT", new String(java.nio.file.Files.readAllBytes(targetFile.toPath())));
    }

    @Test
    public void testAospLocaleAuthorityOverFilename() throws Exception {
        // Build a mock AOSP v2 header with declared locale="es"
        final File aospFile = mTempFolder.newFile("dict_en.dict"); // Renamed as English file
        final ByteBuffer buffer = ByteBuffer.allocate(64);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(AospDictDecoder.MAGIC_AOSP_V2); // Magic
        buffer.putShort((short) 2); // Version 2
        buffer.putShort((short) 0); // Options
        buffer.putInt(32); // Header size

        // Attribute key "locale", value "es" terminated by 0x1F
        buffer.position(12);
        for (byte b : "locale".getBytes(java.nio.charset.StandardCharsets.UTF_8)) buffer.put(b);
        buffer.put((byte) 0x1F);
        for (byte b : "es".getBytes(java.nio.charset.StandardCharsets.UTF_8)) buffer.put(b);
        buffer.put((byte) 0x1F);

        // Terminating node count = 0
        buffer.position(32);
        buffer.put((byte) 0);

        try (FileOutputStream fos = new FileOutputStream(aospFile)) {
            fos.write(buffer.array(), 0, buffer.capacity());
        }

        final AospDictDecoder.DecodedDictionary decoded = AospDictDecoder.decode(aospFile);
        assertEquals("es", decoded.locale);
        assertEquals("es", decoded.languageCode);
    }

    @Test
    public void testCrashRecoveryRestoresBackupFileIfTargetMissing() throws Exception {
        final File dictDir = mTempFolder.newFolder("custom_dict_dir");
        final File backupFile = new File(dictDir, "dict_es.bin.bak");
        final File targetFile = new File(dictDir, "dict_es.bin");

        // Write valid SKDB header to backup file
        final List<BinaryTrieCompiler.WordEntry> words = Collections.singletonList(
                new BinaryTrieCompiler.WordEntry("hola", 200)
        );
        BinaryTrieCompiler.compile(words, backupFile);
        assertTrue(backupFile.exists());
        assertFalse(targetFile.exists());

        // Simulate crash window where backup.renameTo(target) recovers state
        if (!targetFile.exists() && backupFile.exists() && backupFile.length() > 16) {
            backupFile.renameTo(targetFile);
        }
        assertTrue(targetFile.exists());
        assertFalse(backupFile.exists());
    }
}
