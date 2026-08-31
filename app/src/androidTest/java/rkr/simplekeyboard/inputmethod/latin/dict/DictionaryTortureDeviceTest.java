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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import rkr.simplekeyboard.inputmethod.latin.dict.aosp.AospDictDecoder;
import rkr.simplekeyboard.inputmethod.latin.dict.binary.BinaryTrieCompiler;
import rkr.simplekeyboard.inputmethod.latin.dict.binary.BinaryTrieDictionary;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DictionaryTortureDeviceTest {

    private Context mContext;
    private CustomDictionaryManager mManager;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mManager = CustomDictionaryManager.getInstance();
        mManager.deleteCustomDictionary(mContext, "es");
        mManager.deleteCustomDictionary(mContext, "en");
        mManager.deleteCustomDictionary(mContext, "test");
    }

    @After
    public void tearDown() {
        mManager.deleteCustomDictionary(mContext, "es");
        mManager.deleteCustomDictionary(mContext, "en");
        mManager.deleteCustomDictionary(mContext, "test");
    }

    @Test
    public void testCorruptedZeroByteFile() throws IOException {
        final File zeroFile = new File(mContext.getCacheDir(), "zero.dict");
        if (!zeroFile.exists()) zeroFile.createNewFile();
        try {
            final CustomDictionaryManager.ImportResult result =
                    mManager.importDictionary(mContext, Uri.fromFile(zeroFile), "es");
            assertFalse("Zero-byte file must be rejected gracefully", result.success);
        } finally {
            zeroFile.delete();
        }
    }

    @Test
    public void testHostileInvalidMagic() throws IOException {
        final File fakeFile = new File(mContext.getCacheDir(), "invalid_magic.dict");
        try (FileOutputStream fos = new FileOutputStream(fakeFile)) {
            final ByteBuffer buf = ByteBuffer.allocate(64);
            buf.putInt(0xDEADBEEF);
            buf.put(new byte[60]);
            fos.write(buf.array());
        }
        try {
            final CustomDictionaryManager.ImportResult result =
                    mManager.importDictionary(mContext, Uri.fromFile(fakeFile), "es");
            assertFalse("Invalid magic header must be rejected gracefully", result.success);
        } finally {
            fakeFile.delete();
        }
    }

    @Test
    public void testHostileCyclicTrieLoop() throws IOException {
        final File loopFile = new File(mContext.getCacheDir(), "cyclic_loop.dict");
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        final ByteArrayOutputStream attrStream = new ByteArrayOutputStream();
        attrStream.write("locale".getBytes());
        attrStream.write(0x1F);
        attrStream.write("es".getBytes());
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

        // Group 1: 1 node with 1-byte children offset = 0 (pointing back to itself)
        out.write(1); // count = 1
        out.write(0x40 | 0x10); // children 1-byte + terminal
        out.write('a');
        out.write(100); // freq
        out.write(0); // offset = 0 (loop to self)

        try (FileOutputStream fos = new FileOutputStream(loopFile)) {
            fos.write(out.toByteArray());
        }

        try {
            // Must NOT throw StackOverflowError or hang in infinite loop!
            final CustomDictionaryManager.ImportResult result =
                    mManager.importDictionary(mContext, Uri.fromFile(loopFile), "es");
            assertTrue("Cyclic trie handled safely with cycle guard", result.success);
        } finally {
            loopFile.delete();
        }
    }

    @Test
    public void testTruncatedPtNodeStream() throws IOException {
        final File truncatedFile = new File(mContext.getCacheDir(), "truncated.dict");
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        final ByteArrayOutputStream attrStream = new ByteArrayOutputStream();
        attrStream.write("locale".getBytes());
        attrStream.write(0x1F);
        attrStream.write("es".getBytes());
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

        // PtNode count says 100, but file abruptly ends after 2 bytes
        out.write(100);
        out.write(0x20); // flag multi-char

        try (FileOutputStream fos = new FileOutputStream(truncatedFile)) {
            fos.write(out.toByteArray());
        }

        try {
            final CustomDictionaryManager.ImportResult result =
                    mManager.importDictionary(mContext, Uri.fromFile(truncatedFile), "es");
            assertTrue("Truncated stream handled gracefully", result.success);
        } finally {
            truncatedFile.delete();
        }
    }

    @Test
    public void testConcurrentImportAndDeleteTorture() throws InterruptedException, IOException {
        final int THREAD_COUNT = 8;
        final int ITERATIONS = 10;
        final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        final CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        final AtomicInteger errors = new AtomicInteger(0);

        final File sampleFile = new File(mContext.getCacheDir(), "concurrent_sample.bin");
        final List<BinaryTrieCompiler.WordEntry> words = new ArrayList<>();
        words.add(new BinaryTrieCompiler.WordEntry("hola", 200));
        words.add(new BinaryTrieCompiler.WordEntry("mundo", 180));
        BinaryTrieCompiler.compile(words, sampleFile);

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int threadId = t;
            executor.execute(() -> {
                try {
                    for (int i = 0; i < ITERATIONS; i++) {
                        final String lang = "test_" + (threadId % 2);
                        if (i % 2 == 0) {
                            final CustomDictionaryManager.ImportResult res =
                                    mManager.importDictionary(mContext, Uri.fromFile(sampleFile), lang);
                            if (!res.success) {
                                errors.incrementAndGet();
                            }
                        } else {
                            mManager.deleteCustomDictionary(mContext, lang);
                        }
                    }
                } catch (Throwable e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        final boolean finished = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        sampleFile.delete();

        assertTrue("All concurrent operations should finish within timeout", finished);
        assertEquals("No unhandled exceptions during concurrent torture", 0, errors.get());
    }
}
