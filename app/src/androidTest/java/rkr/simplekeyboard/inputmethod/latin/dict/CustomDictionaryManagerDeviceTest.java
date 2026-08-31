package rkr.simplekeyboard.inputmethod.latin.dict;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.List;

import rkr.simplekeyboard.inputmethod.latin.dict.aosp.AospDictDecoder;
import rkr.simplekeyboard.inputmethod.latin.dict.binary.BinaryTrieDictionary;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CustomDictionaryManagerDeviceTest {

    private Context mContext;
    private CustomDictionaryManager mManager;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mManager = CustomDictionaryManager.getInstance();
        mManager.deleteCustomDictionary(mContext, "es");
    }

    @After
    public void tearDown() {
        mManager.deleteCustomDictionary(mContext, "es");
    }

    @Test
    public void testDeviceImportAospDictAndLookup() throws IOException {
        final File testDictFile = new File(mContext.getCacheDir(), "test_main_es.dict");
        createSampleAospDict(testDictFile);

        final Uri uri = Uri.fromFile(testDictFile);
        final CustomDictionaryManager.ImportResult result = mManager.importDictionary(mContext, uri, null);

        assertTrue(result.message, result.success);
        assertEquals("es", result.languageCode);
        assertEquals(2, result.wordCount);

        final File customFile = mManager.getCustomDictionaryFile(mContext, "es");
        assertNotNull(customFile);
        assertTrue(customFile.exists());

        final List<CustomDictionaryManager.CustomDictInfo> installed = mManager.getInstalledDictionaries(mContext);
        assertEquals(1, installed.size());
        assertEquals("es", installed.get(0).languageCode);
        assertEquals(2, installed.get(0).wordCount);

        try (FileInputStream fis = new FileInputStream(customFile);
             FileChannel channel = fis.getChannel()) {
            final ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, customFile.length());
            final BinaryTrieDictionary dict = new BinaryTrieDictionary(buffer);

            assertEquals(2, dict.getWordCount());
            assertTrue(dict.containsWord("hola"));
            assertTrue(dict.containsWord("bien"));
            assertFalse(dict.containsWord("bién"));
            assertEquals(190, dict.getWordFrequency("hola"));
            assertEquals(210, dict.getWordFrequency("bien"));
        }

        final boolean deleted = mManager.deleteCustomDictionary(mContext, "es");
        assertTrue(deleted);
        assertNull(mManager.getCustomDictionaryFile(mContext, "es"));
        assertTrue(mManager.getInstalledDictionaries(mContext).isEmpty());
    }

    @Test
    public void testDeviceImportRealAospDictIfPresent() throws IOException {
        File sdcardFile = new File("/data/local/tmp/main_es.dict");
        if (!sdcardFile.exists()) {
            sdcardFile = new File("/sdcard/Download/main_es.dict");
        }
        if (!sdcardFile.exists() || sdcardFile.length() < 1000) {
            return;
        }

        final File cacheCopy = new File(mContext.getCacheDir(), "main_es_copy.dict");
        try (FileInputStream in = new FileInputStream(sdcardFile);
             FileOutputStream out = new FileOutputStream(cacheCopy)) {
            final byte[] buf = new byte[16384];
            int r;
            while ((r = in.read(buf)) > 0) {
                out.write(buf, 0, r);
            }
        } catch (Exception e) {
            // Raw /sdcard reading blocked by scoped storage on API 34+
            return;
        }

        final Uri uri = Uri.fromFile(cacheCopy);
        final CustomDictionaryManager.ImportResult result = mManager.importDictionary(mContext, uri, null);

        assertTrue(result.message, result.success);
        assertEquals("es", result.languageCode);
        assertTrue("Word count should be > 200000", result.wordCount > 200000);

        final File customFile = mManager.getCustomDictionaryFile(mContext, "es");
        assertNotNull(customFile);
        assertTrue(customFile.exists());

        try (FileInputStream fis = new FileInputStream(customFile);
             FileChannel channel = fis.getChannel()) {
            final ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, customFile.length());
            final BinaryTrieDictionary dict = new BinaryTrieDictionary(buffer);

            assertTrue(dict.getWordCount() > 200000);
            assertTrue(dict.containsWord("bien"));
            assertTrue(dict.containsWord("hola"));
            assertTrue(dict.containsWord("también"));
            assertFalse(dict.containsWord("bién"));
        } finally {
            if (cacheCopy.exists()) {
                cacheCopy.delete();
            }
        }
    }

    private void createSampleAospDict(final File targetFile) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        final ByteArrayOutputStream attrStream = new ByteArrayOutputStream();
        attrStream.write("locale".getBytes());
        attrStream.write(0x1F);
        attrStream.write("es".getBytes());
        attrStream.write(0x1F);
        attrStream.write("dictionary".getBytes());
        attrStream.write(0x1F);
        attrStream.write("main:es".getBytes());
        attrStream.write(0x1F);
        final byte[] attrBytes = attrStream.toByteArray();

        final int headerSize = 12 + attrBytes.length;

        final ByteBuffer headerBuf = ByteBuffer.allocate(12);
        headerBuf.order(ByteOrder.BIG_ENDIAN);
        headerBuf.putInt(AospDictDecoder.MAGIC_AOSP_V202);
        headerBuf.putShort((short) 202);
        headerBuf.putShort((short) 0);
        headerBuf.putInt(headerSize);
        out.write(headerBuf.array());
        out.write(attrBytes);

        // Root PtNodeArray: 2 nodes ("hola", "bien")
        out.write(2);

        // Node 1: "hola"
        out.write(0x20 | 0x10); // Multiple chars + terminal
        out.write('h');
        out.write('o');
        out.write('l');
        out.write('a');
        out.write(0x1F);
        out.write(190); // freq

        // Node 2: "bien"
        out.write(0x20 | 0x10);
        out.write('b');
        out.write('i');
        out.write('e');
        out.write('n');
        out.write(0x1F);
        out.write(210); // freq

        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(out.toByteArray());
        }
    }
}
