package rkr.simplekeyboard.inputmethod.latin.dict.spatial;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpatialTouchModelTest {

    @Test
    public void testExactCenterTouch() {
        SpatialTouchModel model = new SpatialTouchModel();
        // create key 'a' at center 60,60 with width 100 height 100
        model.addKey('a', 60f, 60f, 100f, 100f);
        
        List<SpatialCandidate> candidates = model.getCandidatesForTouch(60f, 60f, -1);
        
        assertEquals(1, candidates.size());
        SpatialCandidate cand = candidates.get(0);
        assertEquals('a', cand.codePoint);
        // Probability should be 1.0 (or very close)
        assertEquals(1.0f, cand.probability, 0.001f);
    }

    @Test
    public void testTouchBetweenKeys() {
        SpatialTouchModel model = new SpatialTouchModel();
        // key 'a' from 0 to 100 -> center 50
        model.addKey('a', 50f, 50f, 100f, 100f);
        // key 's' from 100 to 200 -> center 150
        model.addKey('s', 150f, 50f, 100f, 100f);
        
        // Touch exactly in between (x=100, y=50)
        List<SpatialCandidate> candidates = model.getCandidatesForTouch(100f, 50f, -1);
        
        assertEquals(2, candidates.size());
        
        // Both should have roughly the same probability
        SpatialCandidate cand1 = candidates.get(0);
        SpatialCandidate cand2 = candidates.get(1);
        
        float diff = Math.abs(cand1.probability - cand2.probability);
        assertTrue("Probabilities should be equal", diff < 0.001f);
        
        boolean hasA = cand1.codePoint == 'a' || cand2.codePoint == 'a';
        boolean hasS = cand1.codePoint == 's' || cand2.codePoint == 's';
        assertTrue(hasA && hasS);
    }

    @Test
    public void testZeroAllocationParallelArrayCandidates() {
        SpatialTouchModel model = new SpatialTouchModel();
        model.addKey('a', 50f, 50f, 100f, 100f);
        model.addKey('s', 150f, 50f, 100f, 100f);

        char[] chars = new char[16];
        float[] probs = new float[16];
        float[] logProbs = new float[16];

        int count = model.getCandidatesForTouch(100f, 50f, 'a', chars, probs, logProbs, 16);
        assertEquals(2, count);
        assertEquals(probs[0], probs[1], 0.001f);
        assertTrue(logProbs[0] < 0.0f);
    }

    @Test
    public void testAdjacencyNeighborsEvaluation() {
        SpatialTouchModel model = new SpatialTouchModel();
        // Keyboard row: c, v, b, f, g
        model.addKey('c', 250f, 150f, 100f, 100f);
        model.addKey('v', 350f, 150f, 100f, 100f);
        model.addKey('b', 450f, 150f, 100f, 100f);
        model.addKey('f', 300f, 50f, 100f, 100f);
        model.addKey('g', 400f, 50f, 100f, 100f);

        // Touch slightly right of 'v' towards 'b' (x=380, y=150)
        char[] chars = new char[16];
        float[] probs = new float[16];
        float[] logProbs = new float[16];

        int count = model.getCandidatesForTouch(380f, 150f, 'v', chars, probs, logProbs, 16);
        assertTrue("Should include at least 'v' and 'b'", count >= 2);
        // 'v' and 'b' should be top candidates
        assertTrue(chars[0] == 'v' || chars[0] == 'b');
        assertTrue(chars[1] == 'v' || chars[1] == 'b');
        // Probabilities must decrease with distance
        assertTrue(probs[0] >= probs[1]);
    }

    @Test
    public void testFallbackWhenOutOfBounds() {
        SpatialTouchModel model = new SpatialTouchModel();
        model.addKey('z', 50f, 50f, 100f, 100f);

        char[] chars = new char[16];
        float[] probs = new float[16];
        float[] logProbs = new float[16];

        // Touch far away with fallback 'z'
        int count = model.getCandidatesForTouch(900f, 900f, 'z', chars, probs, logProbs, 16);
        assertEquals(1, count);
        assertEquals('z', chars[0]);
        assertEquals(1.0f, probs[0], 0.001f);
        assertEquals(0.0f, logProbs[0], 0.001f);
    }
}

