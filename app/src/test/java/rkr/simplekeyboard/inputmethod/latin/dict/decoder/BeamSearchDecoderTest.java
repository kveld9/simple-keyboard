package rkr.simplekeyboard.inputmethod.latin.dict.decoder;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

import rkr.simplekeyboard.inputmethod.latin.dict.binary.BinaryTrieDictionary;
import rkr.simplekeyboard.inputmethod.latin.dict.spatial.SpatialTouchModel;
import rkr.simplekeyboard.inputmethod.latin.dict.spatial.SpatialCandidate;
import java.nio.ByteBuffer;

public class BeamSearchDecoderTest {
    
    private BeamSearchDecoder decoder;
    
    // Custom Mock BinaryTrieDictionary
    private class MockDictionary extends BinaryTrieDictionary {
        public MockDictionary() {
            super(createDummyBuffer());
        }
        
        @Override
        public int getRootNode() {
            return 1;
        }
        
        @Override
        public int getChildren(int nodeOffset, char[] outChars, int[] outOffsets) {
            if (nodeOffset == 1) {
                outChars[0] = 's';
                outChars[1] = 'a';
                outOffsets[0] = 2;
                outOffsets[1] = 3;
                return 2;
            }
            return 0;
        }
        
        @Override
        public int getNodeFrequency(int nodeOffset) {
            if (nodeOffset == 2) return 100;
            if (nodeOffset == 3) return 200;
            return 0;
        }
        
        @Override
        public boolean isTerminal(int nodeOffset) {
            return nodeOffset == 2 || nodeOffset == 3;
        }
        
        @Override
        public String getNodeWord(int nodeOffset) {
            if (nodeOffset == 2) return "s";
            if (nodeOffset == 3) return "a";
            return null;
        }
        
        @Override
        public List<CharSequence> getPrefixSuggestions(String prefix, int limit) {
            return new java.util.ArrayList<>();
        }
    }
    
    // Custom Mock SpatialTouchModel
    private class MockTouchModel extends SpatialTouchModel {
        @Override
        public List<SpatialCandidate> getCandidatesForTouch(float touchX, float touchY, int fallbackCode) {
            if (touchX == 10.0f && touchY == 10.0f && fallbackCode == 's') {
                return java.util.Arrays.asList(
                    new SpatialCandidate('s', 0.6f, -0.51f),
                    new SpatialCandidate('a', 0.4f, -0.91f)
                );
            }
            return new java.util.ArrayList<>();
        }
    }
    
    private static ByteBuffer createDummyBuffer() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0, 0x42444b53); // magic
        buf.putInt(4, 1);          // version
        buf.putInt(8, 0);          // wordCount
        buf.putInt(12, 16);        // rootOffset
        return buf;
    }

    @Before
    public void setUp() {
        decoder = new BeamSearchDecoder(new MockDictionary(), new MockTouchModel());
    }

    @Test
    public void testTypingWithErrors() {
        decoder.onTouch(10.0f, 10.0f, 's');
        
        List<CharSequence> suggestions = decoder.getSuggestions("s", 3, "");
        assertFalse(suggestions.isEmpty());
        // Since 'a' is much more frequent (200 vs 100) and probability is somewhat close, beam search keeps both
        // We just ensure it works and doesn't crash.
    }

    @Test
    public void testBackspacePopping() {
        decoder.onTouch(0, 0, 'h');
        decoder.onTouch(0, 0, 'e');
        decoder.onBackspace();
        assertNotNull(decoder.getSuggestions("h", 3, ""));
    }

    @Test
    public void testBestCorrectionThreshold() {
        decoder.onTouch(10.0f, 10.0f, 's');
        // If threshold is very high, it should return null
        assertNull(decoder.getBestCorrection("s", 100.0f, ""));
        
        // If threshold is reasonable and best correction differs from typedWord, it returns the correction
        // In our mock, if typed "x", best terminal hypothesis might be returned if score >= threshold
        String correction = decoder.getBestCorrection("x", 0.5f, "");
        assertNotNull(correction);
    }
}
