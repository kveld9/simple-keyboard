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
 * Unit tests for {@link MicroTransformerModel} pure Java TRF1 inference engine.
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
     * Helper to create a complete valid TRF1 binary file with custom hyperparameters,
     * vocabulary, embeddings, and layer weights.
     */
    private File createTestTRF1File(
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

        // Per layer sizes
        int qkvCount = (3 * dModel) * dModel;
        int projCount = dModel * dModel;
        int mlpUpCount = dFf * dModel;
        int mlpDownCount = dModel * dFf;
        int layerSize = qkvCount + projCount + mlpUpCount + mlpDownCount + (dModel * 4) + (dModel * 4);

        int totalFileSize = offLayer0 + nLayers * layerSize;

        ByteBuffer buffer = ByteBuffer.allocate(totalFileSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // --- Write Header (64 bytes) ---
        buffer.position(0);
        buffer.putInt(MicroTransformerModel.MAGIC_TRF1); // 0x00: magic
        buffer.putInt(1);                                // 0x04: version
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
                buffer.put((byte) 1);
            }
        }

        // --- Write Layers (at offLayer0) ---
        buffer.position(offLayer0);
        for (int l = 0; l < nLayers; l++) {
            // 1. QKV weights
            if (qkvWeights != null) {
                int toWrite = Math.min(qkvWeights.length, qkvCount);
                buffer.put(qkvWeights, 0, toWrite);
                for (int i = toWrite; i < qkvCount; i++) buffer.put((byte) 0);
            } else {
                for (int i = 0; i < qkvCount; i++) buffer.put((byte) 1);
            }

            // 2. Proj weights
            if (projWeights != null) {
                int toWrite = Math.min(projWeights.length, projCount);
                buffer.put(projWeights, 0, toWrite);
                for (int i = toWrite; i < projCount; i++) buffer.put((byte) 0);
            } else {
                for (int i = 0; i < projCount; i++) buffer.put((byte) 1);
            }

            // 3. MLP Up weights
            if (mlpUpWeights != null) {
                int toWrite = Math.min(mlpUpWeights.length, mlpUpCount);
                buffer.put(mlpUpWeights, 0, toWrite);
                for (int i = toWrite; i < mlpUpCount; i++) buffer.put((byte) 0);
            } else {
                for (int i = 0; i < mlpUpCount; i++) buffer.put((byte) 1);
            }

            // 4. MLP Down weights
            if (mlpDownWeights != null) {
                int toWrite = Math.min(mlpDownWeights.length, mlpDownCount);
                buffer.put(mlpDownWeights, 0, toWrite);
                for (int i = toWrite; i < mlpDownCount; i++) buffer.put((byte) 0);
            } else {
                for (int i = 0; i < mlpDownCount; i++) buffer.put((byte) 1);
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
        }

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(buffer.array());
        }

        return file;
    }

    /**
     * Overload with common defaults.
     */
    private File createTestTRF1File(
            File file,
            int vocabSize,
            int dModel,
            int nHeads,
            String[] bpeTokens,
            byte[] embeddingData) throws IOException {
        return createTestTRF1File(
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
        createTestTRF1File(modelFile, 16, 4, 2, null, null);

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
        createTestTRF1File(invalidFile, 16, 4, 2, null, null);

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
        createTestTRF1File(modelFile, 16, 4, 2, null, null);

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
        createTestTRF1File(modelFile, 16, 4, 2, vocab, null);

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
        createTestTRF1File(modelFile, vocabSize, dModel, nHeads, null, emb);

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

        createTestTRF1File(modelFile, vocabSize, dModel, nHeads, null, null);

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

        createTestTRF1File(modelFile, vocabSize, dModel, nHeads, null, emb);

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

        createTestTRF1File(
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
        createTestTRF1File(modelFile, 16, 4, 2, vocab, null);

        final MicroTransformerModel model = new MicroTransformerModel();
        assertTrue(model.loadModel(modelFile));

        assertEquals("Word start marker \u2581 should be replaced by space", " hola", model.getTokenText(2));
        assertEquals("Word start marker \u2581 should be replaced by space", " mundo", model.getTokenText(3));
        assertEquals("Plain token should remain unchanged", "plain", model.getTokenText(4));
        assertEquals("Out of bounds token ID should return empty string", "", model.getTokenText(999));
    }
}
