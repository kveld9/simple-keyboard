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
}
