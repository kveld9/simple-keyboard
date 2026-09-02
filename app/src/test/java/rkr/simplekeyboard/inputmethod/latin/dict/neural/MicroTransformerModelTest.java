package rkr.simplekeyboard.inputmethod.latin.dict.neural;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link MicroTransformerModel} C++ Native TRF2 (1.58-bit Ternary) inference engine via JNI.
 */
public class MicroTransformerModelTest {

    private static final String TAG = "MicroTransformerModelTest";

    @Rule
    public TemporaryFolder mTempFolder = new TemporaryFolder();

    /**
     * Helper to align offsets to 64 bytes.
     */
    private static int align64(int offset) {
        return (offset + 63) & ~63;
    }

    /**
     * Helper to create a complete valid TRF2 binary file with custom hyperparameters,
     * vocabulary, embeddings, and layer weights.
     */
    private File createTestTRF2File(
            File file,
            int vocabSize,
            int dModel,
            int nHeads,
            int nLayers,
            float scaleEmb,
            float scalePos,
            float scaleDot,
            String[] bpeTokens,
            byte[] embeddingData,
            byte[] posData,
            byte[] qkvWeights,
            byte[] projWeights,
            byte[] mlpUpWeights,
            byte[] mlpDownWeights,
            float[] gamma1,
            float[] gamma2) throws IOException {

        int dFf = 2 * dModel;

        // 1. Calculate BPE section size
        int bpeStringPoolSize = 0;
        byte[][] tokenBytes = new byte[vocabSize][];
        for (int i = 0; i < vocabSize; i++) {
            String tok = (bpeTokens != null && i < bpeTokens.length && bpeTokens[i] != null)
                    ? bpeTokens[i] : ("tok" + i);
            tokenBytes[i] = tok.getBytes(StandardCharsets.UTF_8);
            bpeStringPoolSize += tokenBytes[i].length + 1; // null-terminated
        }
        int bpeSectionSize = 4 + 4 * vocabSize + bpeStringPoolSize;

        // 2. Compute offsets aligned to 64 bytes
        int offBpe = 64;
        int offEmb = align64(offBpe + bpeSectionSize);
        int embSize = vocabSize * dModel;
        int offPos = align64(offEmb + embSize);
        int posSize = MicroTransformerModel.MAX_SEQ_LEN * dModel;
        int offLayer0 = align64(offPos + posSize);

        // Per layer sizes (In TRF2, weights are packed 4 per byte)
        int qkvPacked = (3 * dModel * dModel + 3) / 4;
        int projPacked = (dModel * dModel + 3) / 4;
        int mlpUpPacked = (dFf * dModel + 3) / 4;
        int mlpDownPacked = (dModel * dFf + 3) / 4;
        int layerSize = qkvPacked + projPacked + mlpUpPacked + mlpDownPacked + (dModel * 4) + (dModel * 4) + 4 + 4;
        int layerStride = align64(layerSize);

        int totalFileSize = offLayer0 + nLayers * layerStride;

        ByteBuffer buffer = ByteBuffer.allocate(totalFileSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // --- Write Header (64 bytes) ---
        buffer.position(0);
        buffer.putInt(MicroTransformerModel.MAGIC_TRF2); // 0x00: magic (TRF2)
        buffer.putInt(2);                                // 0x04: version (2 for TRF2)
        buffer.putInt(vocabSize);                        // 0x08: vocab_size
        buffer.putInt(dModel);                           // 0x0C: d_model
        buffer.putInt(nHeads);                           // 0x10: n_heads
        buffer.putInt(nLayers);                          // 0x14: n_layers
        buffer.putInt(offBpe);                           // 0x18: off_bpe
        buffer.putInt(offEmb);                           // 0x1C: off_emb
        buffer.putInt(offPos);                           // 0x20: off_pos
        buffer.putInt(offLayer0);                        // 0x24: off_layer0
        buffer.putFloat(scaleEmb);                       // 0x28: scale_emb
        buffer.putFloat(scalePos);                       // 0x2C: scale_pos
        buffer.putFloat(scaleDot);                       // 0x30: scale_dot
        // 0x34 - 0x3F: reserved padding to 64 bytes

        // --- Write BPE Table (at offBpe) ---
        buffer.position(offBpe);
        buffer.putInt(vocabSize);
        int currentStrOffset = 0;
        for (int i = 0; i < vocabSize; i++) {
            buffer.putInt(currentStrOffset);
            currentStrOffset += tokenBytes[i].length + 1;
        }
        for (int i = 0; i < vocabSize; i++) {
            buffer.put(tokenBytes[i]);
            buffer.put((byte) 0); // null terminator
        }

        // --- Write Embeddings (at offEmb) ---
        buffer.position(offEmb);
        if (embeddingData != null) {
            int toWrite = Math.min(embeddingData.length, embSize);
            buffer.put(embeddingData, 0, toWrite);
            for (int i = toWrite; i < embSize; i++) {
                buffer.put((byte) 0);
            }
        } else {
            for (int i = 0; i < embSize; i++) {
                buffer.put((byte) 1);
            }
        }

        // --- Write Positional Embeddings (at offPos) ---
        buffer.position(offPos);
        if (posData != null) {
            int toWrite = Math.min(posData.length, posSize);
            buffer.put(posData, 0, toWrite);
            for (int i = toWrite; i < posSize; i++) {
                buffer.put((byte) 0);
            }
        } else {
            for (int i = 0; i < posSize; i++) {
                buffer.put((byte) 0);
            }
        }

        // --- Write Layers (at offLayer0) with packed 2-bit weights ---
        byte defaultPacked = (byte) (0x01 | (0x01 << 2) | (0x01 << 4) | (0x01 << 6)); // all +1.0f
        for (int l = 0; l < nLayers; l++) {
            buffer.position(offLayer0 + l * layerStride);
            // 1. QKV weights
            if (qkvWeights != null) {
                int toWrite = Math.min(qkvWeights.length, qkvPacked);
                buffer.put(qkvWeights, 0, toWrite);
                for (int i = toWrite; i < qkvPacked; i++) buffer.put((byte) 0);
            } else {
                for (int i = 0; i < qkvPacked; i++) buffer.put(defaultPacked);
            }

            // 2. Proj weights
            if (projWeights != null) {
                int toWrite = Math.min(projWeights.length, projPacked);
                buffer.put(projWeights, 0, toWrite);
                for (int i = toWrite; i < projPacked; i++) buffer.put((byte) 0);
            } else {
                for (int i = 0; i < projPacked; i++) buffer.put(defaultPacked);
            }

            // 3. MLP Up weights
            if (mlpUpWeights != null) {
                int toWrite = Math.min(mlpUpWeights.length, mlpUpPacked);
                buffer.put(mlpUpWeights, 0, toWrite);
                for (int i = toWrite; i < mlpUpPacked; i++) buffer.put((byte) 0);
            } else {
                for (int i = 0; i < mlpUpPacked; i++) buffer.put(defaultPacked);
            }

            // 4. MLP Down weights
            if (mlpDownWeights != null) {
                int toWrite = Math.min(mlpDownWeights.length, mlpDownPacked);
                buffer.put(mlpDownWeights, 0, toWrite);
                for (int i = toWrite; i < mlpDownPacked; i++) buffer.put((byte) 0);
            } else {
                for (int i = 0; i < mlpDownPacked; i++) buffer.put(defaultPacked);
            }

            // 5. Gamma1 fused
            for (int i = 0; i < dModel; i++) {
                float g = (gamma1 != null && i < gamma1.length) ? gamma1[i] : 1.0f;
                buffer.putFloat(g);
            }

            // 6. Gamma2 fused
            for (int i = 0; i < dModel; i++) {
                float g = (gamma2 != null && i < gamma2.length) ? gamma2[i] : 1.0f;
                buffer.putFloat(g);
            }

            // 7. Scale Proj & Scale Down
            buffer.putFloat(1.0f);
            buffer.putFloat(1.0f);
        }

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(buffer.array());
        }

        return file;
    }

    /**
     * Overload with common defaults.
     */
    private File createTestTRF2File(
            File file,
            int vocabSize,
            int dModel,
            int nHeads,
            String[] bpeTokens,
            byte[] embeddingData) throws IOException {
        return createTestTRF2File(
                file,
                vocabSize,
                dModel,
                nHeads,
                2,          // nLayers
                0.1f,       // scaleEmb
                0.05f,      // scalePos
                0.001f,     // scaleDot
                bpeTokens,
                embeddingData,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Test
    public void testLoadValidTRF1() throws IOException {
        final File modelFile = mTempFolder.newFile("valid_model.trf1");
        createTestTRF2File(modelFile, 16, 4, 2, null, null);

        final MicroTransformerModel model = new MicroTransformerModel();
        final boolean loaded = model.loadModel(modelFile);

        assertTrue("loadModel should return true for valid TRF1", loaded);
        assertTrue("isLoaded should be true", model.isLoaded());
        assertEquals("Vocab size should match header", 16, model.getVocabSize());
        assertEquals("Model dim should match header", 4, model.getModelDim());
    }

    @Test
    public void testLoadInvalidMagic() throws IOException {
        final File invalidFile = mTempFolder.newFile("invalid_magic.trf1");
        createTestTRF2File(invalidFile, 16, 4, 2, null, null);

        // Corrupt the magic number at offset 0
        try (FileOutputStream fos = new FileOutputStream(invalidFile, false)) {
            ByteBuffer buf = ByteBuffer.allocate(64);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.putInt(0xDEADBEEF); // invalid magic
            fos.write(buf.array());
        }

        final MicroTransformerModel model = new MicroTransformerModel();
        final boolean loaded = model.loadModel(invalidFile);

        assertFalse("loadModel should return false for invalid magic", loaded);
        assertFalse("isLoaded should be false", model.isLoaded());
    }

    @Test
    public void testLoadFileTooSmall() throws IOException {
        final File smallFile = mTempFolder.newFile("small.trf1");
        try (FileOutputStream fos = new FileOutputStream(smallFile)) {
            fos.write(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}); // 10 bytes
        }

        final MicroTransformerModel model = new MicroTransformerModel();
        final boolean loaded = model.loadModel(smallFile);

        assertFalse("loadModel should return false when file size is under 64 bytes", loaded);
        assertFalse("isLoaded should be false", model.isLoaded());
    }

    @Test
    public void testUnload() throws IOException {
        final File modelFile = mTempFolder.newFile("model_unload.trf1");
        createTestTRF2File(modelFile, 16, 4, 2, null, null);

        final MicroTransformerModel model = new MicroTransformerModel();
        assertTrue(model.loadModel(modelFile));
        assertTrue(model.isLoaded());

        model.unload();

        assertFalse("isLoaded should be false after unload()", model.isLoaded());
        assertEquals(0, model.getVocabSize());
        assertEquals(0, model.getModelDim());
    }

    @Test
    public void testTokenizeBasic() throws IOException {
        final String[] vocab = new String[16];
        vocab[0] = "<pad>";
        vocab[1] = "<unk>";
        vocab[2] = "\u2581hola";
        vocab[3] = "\u2581mundo";
        for (int i = 4; i < 16; i++) {
            vocab[i] = "tok" + i;
        }

        final File modelFile = mTempFolder.newFile("tokenize_model.trf1");
        createTestTRF2File(modelFile, 16, 4, 2, vocab, null);

        final MicroTransformerModel model = new MicroTransformerModel();
        assertTrue(model.loadModel(modelFile));

        final int[] tokens = model.tokenize("hola mundo", 16);
        assertNotNull(tokens);
        assertEquals(2, tokens.length);
        assertEquals(2, tokens[0]); // \u2581hola -> ID 2
        assertEquals(3, tokens[1]); // \u2581mundo -> ID 3

        // Test maxTokens clamping
        final int[] singleToken = model.tokenize("hola mundo", 1);
        assertEquals(1, singleToken.length);
        assertEquals(2, singleToken[0]);

        // Test tokenize when not loaded returns empty array
        model.unload();
        final int[] emptyTokens = model.tokenize("hola mundo", 16);
        assertNotNull(emptyTokens);
        assertEquals(0, emptyTokens.length);
    }

    @Test
    public void testForwardProducesNonZeroOutput() throws IOException {
        final int vocabSize = 16;
        final int dModel = 4;
        final int nHeads = 2;
        final File modelFile = mTempFolder.newFile("forward_nonzero.trf1");

        byte[] emb = new byte[vocabSize * dModel];
        Arrays.fill(emb, (byte) 2);
        createTestTRF2File(modelFile, vocabSize, dModel, nHeads, null, emb);

        final MicroTransformerModel model = new MicroTransformerModel();
        assertTrue(model.loadModel(modelFile));

        final int[] contextTokens = new int[]{2, 3};
        final float[] outHidden = new float[dModel];
        final boolean success = model.forward(contextTokens, contextTokens.length, outHidden);

        assertTrue("forward should succeed", success);

        boolean hasNonZero = false;
        for (int i = 0; i < dModel; i++) {
            if (Math.abs(outHidden[i]) > 1e-6f) {
                hasNonZero = true;
                break;
            }
        }
        assertTrue("outHidden must contain non-zero values after forward pass", hasNonZero);
    }

    @Test
    public void testForwardDeterministic() throws IOException {
        final int vocabSize = 16;
        final int dModel = 4;
        final int nHeads = 2;
        final File modelFile = mTempFolder.newFile("forward_deterministic.trf1");

        createTestTRF2File(modelFile, vocabSize, dModel, nHeads, null, null);

        final MicroTransformerModel model = new MicroTransformerModel();
        assertTrue(model.loadModel(modelFile));

        final int[] contextTokens = new int[]{1, 2, 3};
        final float[] outHidden1 = new float[dModel];
        final float[] outHidden2 = new float[dModel];

        assertTrue(model.forward(contextTokens, contextTokens.length, outHidden1));
        assertTrue(model.forward(contextTokens, contextTokens.length, outHidden2));

        assertArrayEquals("Forward pass must be deterministic for identical inputs",
                outHidden1, outHidden2, 1e-7f);
    }

    @Test
    public void testScoreCandidatesOrdering() throws IOException {
        final int vocabSize = 16;
        final int dModel = 4;
        final int nHeads = 2;
        final File modelFile = mTempFolder.newFile("score_ordering.trf1");

        // Candidate A (ID 2): positive embedding [10, 10, 0, 0]
        // Candidate B (ID 3): negative embedding [-10, -10, 0, 0]
        final byte[] emb = new byte[vocabSize * dModel];
        emb[2 * dModel + 0] = 10;
        emb[2 * dModel + 1] = 10;
        emb[2 * dModel + 2] = 0;
        emb[2 * dModel + 3] = 0;

        emb[3 * dModel + 0] = -10;
        emb[3 * dModel + 1] = -10;
        emb[3 * dModel + 2] = 0;
        emb[3 * dModel + 3] = 0;

        createTestTRF2File(modelFile, vocabSize, dModel, nHeads, null, emb);

        final MicroTransformerModel model = new MicroTransformerModel();
        assertTrue(model.loadModel(modelFile));

        final float[] hT = new float[]{1.0f, 1.0f, 0.0f, 0.0f};
        final int[] candidateIds = new int[]{2, 3};
        final float[] outLogits = new float[2];

        model.scoreCandidates(hT, candidateIds, 2, outLogits);

        assertTrue("Candidate A logit (" + outLogits[0] + ") should be higher than Candidate B logit ("
                + outLogits[1] + ")", outLogits[0] > outLogits[1]);
    }

    @Test
    public void testScoreCandidatesScaleDot() throws IOException {
        final int vocabSize = 16;
        final int dModel = 4;
        final int nHeads = 2;
        final float scaleEmb = 0.1f;
        final float scaleDot = 0.05f;
        final File modelFile = mTempFolder.newFile("score_scale_dot.trf1");

        // Candidate ID 2: embedding raw values [10, 20, 30, 40]
        // Dequantized embedding = [1.0f, 2.0f, 3.0f, 4.0f]
        final byte[] emb = new byte[vocabSize * dModel];
        emb[2 * dModel + 0] = 10;
        emb[2 * dModel + 1] = 20;
        emb[2 * dModel + 2] = 30;
        emb[2 * dModel + 3] = 40;

        createTestTRF2File(
                modelFile,
                vocabSize,
                dModel,
                nHeads,
                2,          // nLayers
                scaleEmb,
                0.05f,
                scaleDot,
                null,
                emb,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        final MicroTransformerModel model = new MicroTransformerModel();
        assertTrue(model.loadModel(modelFile));

        final float[] hT = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        final int[] candidateIds = new int[]{2};
        final float[] outLogits = new float[1];

        // dot(hT, emb[2]) = 1.0*1.0 + 1.0*2.0 + 1.0*3.0 + 1.0*4.0 = 10.0f
        // expected logit = 10.0f * scaleDot = 0.5f
        model.scoreCandidates(hT, candidateIds, 1, outLogits);

        assertEquals("Logit must equal dot(hT, embedding) * scale_dot",
                0.5f, outLogits[0], 1e-5f);
    }

    @Test
    public void testGetTokenText() throws IOException {
        final String[] vocab = new String[16];
        vocab[0] = "<pad>";
        vocab[1] = "<unk>";
        vocab[2] = "\u2581hola";
        vocab[3] = "\u2581mundo";
        vocab[4] = "plain";
        for (int i = 5; i < 16; i++) {
            vocab[i] = "tok" + i;
        }

        final File modelFile = mTempFolder.newFile("get_token_text.trf1");
        createTestTRF2File(modelFile, 16, 4, 2, vocab, null);

        final MicroTransformerModel model = new MicroTransformerModel();
        assertTrue(model.loadModel(modelFile));

        assertEquals("Word start marker \u2581 should be replaced by space", " hola", model.getTokenText(2));
        assertEquals("Word start marker \u2581 should be replaced by space", " mundo", model.getTokenText(3));
        assertEquals("Plain token should remain unchanged", "plain", model.getTokenText(4));
        assertEquals("Out of bounds token ID should return empty string", "", model.getTokenText(999));
    }

    @Test
    public void testTokenizeTail() throws IOException {
        final String[] vocab = new String[16];
        vocab[0] = "<pad>";
        vocab[1] = "<unk>";
        vocab[2] = "\u2581hola";
        vocab[3] = "\u2581amigo";
        vocab[4] = "\u2581mundo";
        for (int i = 5; i < 16; i++) {
            vocab[i] = "tok" + i;
        }

        final File modelFile = mTempFolder.newFile("tokenize_tail.trf2");
        createTestTRF2File(modelFile, 16, 4, 2, vocab, null);

        final MicroTransformerModel model = new MicroTransformerModel();
        assertTrue(model.loadModel(modelFile));

        final int[] outTokens = new int[2];
        // "hola amigo mundo" -> 3 tokens: [2, 3, 4]. Tail of 2 should be [3, 4]
        int count = model.tokenizeTail("hola amigo mundo", outTokens, 2);
        assertEquals(2, count);
        assertEquals(3, outTokens[0]);
        assertEquals(4, outTokens[1]);
    }

    @Test
    public void testForwardLongContextUsesTail() throws IOException {
        final String[] vocab = new String[16];
        vocab[0] = "<pad>";
        vocab[1] = "<unk>";
        for (int i = 2; i < 16; i++) {
            vocab[i] = "tok" + i;
        }

        final File modelFile = mTempFolder.newFile("forward_long_context.trf2");
        createTestTRF2File(modelFile, 16, 4, 2, vocab, null);

        final MicroTransformerModel model = new MicroTransformerModel();
        assertTrue(model.loadModel(modelFile));

        // Create an array of 40 tokens (> MAX_SEQ_LEN of 32)
        final int[] longTokens = new int[40];
        for (int i = 0; i < 40; i++) {
            longTokens[i] = (i % 14) + 2;
        }

        final float[] outHiddenLong = new float[4];
        assertTrue(model.forward(longTokens, 40, outHiddenLong));

        // Forward with only the last 32 tokens should produce exact same output
        final int[] tailTokens = new int[32];
        System.arraycopy(longTokens, 8, tailTokens, 0, 32);
        final float[] outHiddenTail = new float[4];
        assertTrue(model.forward(tailTokens, 32, outHiddenTail));

        assertArrayEquals(outHiddenTail, outHiddenLong, 1e-5f);
    }

    @Test
    public void testGoldenPyTorchParity() throws Exception {
        java.net.URL url = getClass().getClassLoader().getResource("golden.trf2");
        assertNotNull("golden.trf2 not found in resources", url);
        File modelFile = new File(url.getFile());

        MicroTransformerModel model = new MicroTransformerModel();
        assertTrue("Model should load", model.loadModel(modelFile));

        int[] ctx = new int[]{2, 3, 4, 2, 5, 6, 7};
        float[] actual = new float[model.getModelDim()];
        assertTrue("Forward should succeed", model.forward(ctx, ctx.length, actual));

        float[] expected = new float[]{
            -1.166689157485962f, -0.8028096556663513f, -0.5355265140533447f, 1.587156057357788f, -0.6363348960876465f, -0.5477361679077148f, -1.9730464220046997f, 0.5610584020614624f, 1.406684160232544f, -1.0278081893920898f, -0.9140545129776001f, 0.25515520572662354f, -1.0077097415924072f, -0.01640675775706768f, -1.612504005432129f, 0.39476650953292847f, 1.4428497552871704f, 0.03957396373152733f, 0.4437595307826996f, 1.2459654808044434f, 0.9290741682052612f, -0.31558796763420105f, -0.5646006464958191f, -0.3390495181083679f, -1.1926425695419312f, -0.3671950399875641f, -0.6316089630126953f, 0.669793426990509f, 1.2768467664718628f, 1.8079184293746948f, 1.1938163042068481f, -1.1458957195281982f, 1.1423856019973755f, 0.67339026927948f, 0.873499870300293f, -0.27465561032295227f, 0.61070716381073f, -0.3854897618293762f, -2.0165464878082275f, -0.5828003287315369f, 0.5925933122634888f, -0.5015355944633484f, 1.1210907697677612f, -0.9018316864967346f, -1.2840843200683594f, -0.16861285269260406f, 0.25915029644966125f, 0.5531975626945496f, 1.0499356985092163f, -0.9667774438858032f, -0.2067382037639618f, 0.6537514328956604f, -1.1431269645690918f, -0.41201838850975037f, -1.45578134059906f, 0.32293227314949036f, -1.4459483623504639f, -2.298086166381836f, 2.009270191192627f, 0.5249819159507751f, -0.1380164623260498f, 0.5097774267196655f, -0.11594837158918381f, 0.9297766089439392f
        };

        assertEquals("Dimension mismatch", expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("Mismatch at index " + i, expected[i], actual[i], 1e-4f);
        }
    }
}
