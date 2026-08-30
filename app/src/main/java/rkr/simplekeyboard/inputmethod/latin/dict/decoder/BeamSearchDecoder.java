package rkr.simplekeyboard.inputmethod.latin.dict.decoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import rkr.simplekeyboard.inputmethod.latin.common.StringUtils;
import rkr.simplekeyboard.inputmethod.latin.dict.binary.BinaryTrieDictionary;
import rkr.simplekeyboard.inputmethod.latin.dict.spatial.SpatialCandidate;
import rkr.simplekeyboard.inputmethod.latin.dict.spatial.SpatialTouchModel;

public class BeamSearchDecoder {
    private static final int K = 8;
    private static final float LAMBDA = 0.5f;

    public static class BeamHypothesis implements Comparable<BeamHypothesis> {
        public final String word;
        public final int nodeOffset;
        public final float spatialLogScore;
        public final float totalScore;

        public BeamHypothesis(String word, int nodeOffset, float spatialLogScore, float totalScore) {
            this.word = word;
            this.nodeOffset = nodeOffset;
            this.spatialLogScore = spatialLogScore;
            this.totalScore = totalScore;
        }

        @Override
        public int compareTo(BeamHypothesis o) {
            return Float.compare(o.totalScore, this.totalScore); // descending
        }
    }

    private final BinaryTrieDictionary dictionary;
    private final SpatialTouchModel spatialModel;
    private final Stack<List<BeamHypothesis>> states;
    private final char[] mChildrenChars = new char[256];
    private final int[] mChildrenOffsets = new int[256];

    public BeamSearchDecoder(BinaryTrieDictionary dictionary, SpatialTouchModel spatialModel) {
        this.dictionary = dictionary;
        this.spatialModel = spatialModel;
        this.states = new Stack<>();
        reset();
    }

    public void reset() {
        states.clear();
        List<BeamHypothesis> initial = new ArrayList<>();
        int rootNode = dictionary != null ? dictionary.getRootNode() : -1;
        initial.add(new BeamHypothesis("", rootNode, 0.0f, 0.0f));
        states.push(initial);
    }

    public void onTouch(float x, float y, char rawChar) {
        final List<BeamHypothesis> current = getCurrentHypotheses();
        final List<SpatialCandidate> candidates = getTouchCandidates(x, y, rawChar);
        List<BeamHypothesis> next = expandHypotheses(current, candidates);
        if (next.isEmpty()) {
            next = createFallbackHypotheses(current, rawChar);
        }
        pruneAndPushState(next);
    }

    private List<BeamHypothesis> getCurrentHypotheses() {
        return states.isEmpty() ? new ArrayList<BeamHypothesis>() : states.peek();
    }

    private List<SpatialCandidate> getTouchCandidates(float x, float y, char rawChar) {
        List<SpatialCandidate> candidates = spatialModel != null ? spatialModel.getCandidatesForTouch(x, y, rawChar) : new ArrayList<SpatialCandidate>();
        if (candidates.isEmpty()) {
            candidates.add(SpatialCandidate.exact(rawChar));
        }
        return candidates;
    }

    private List<BeamHypothesis> expandHypotheses(List<BeamHypothesis> current, List<SpatialCandidate> candidates) {
        List<BeamHypothesis> next = new ArrayList<>();
        if (dictionary == null) {
            return next;
        }
        for (BeamHypothesis hyp : current) {
            if (hyp.nodeOffset > 0) {
                expandSingleHypothesis(hyp, candidates, next);
            }
        }
        return next;
    }

    private void expandSingleHypothesis(BeamHypothesis hyp, List<SpatialCandidate> candidates, List<BeamHypothesis> next) {
        int count = dictionary.getChildren(hyp.nodeOffset, mChildrenChars, mChildrenOffsets);

        for (int i = 0; i < count; i++) {
            matchChildWithCandidates(hyp, mChildrenChars[i], mChildrenOffsets[i], candidates, next);
        }
    }

    private void matchChildWithCandidates(BeamHypothesis hyp, char childChar, int childNode, List<SpatialCandidate> candidates, List<BeamHypothesis> next) {
        final char foldedChild = StringUtils.foldChar(childChar);
        for (SpatialCandidate cand : candidates) {
            if (foldedChild == StringUtils.foldChar(cand.codePoint)) {
                next.add(createHypothesis(hyp, childChar, childNode, cand));
            }
        }
    }

    private BeamHypothesis createHypothesis(BeamHypothesis hyp, char childChar, int childNode, SpatialCandidate cand) {
        int freq = dictionary.getNodeFrequency(childNode);
        float freqLog = freq > 0 ? (float) Math.log(freq) : 0.0f;
        float newSpatialLogScore = hyp.spatialLogScore + cand.logProb;
        float newTotalScore = newSpatialLogScore + LAMBDA * freqLog;
        return new BeamHypothesis(hyp.word + childChar, childNode, newSpatialLogScore, newTotalScore);
    }

    private List<BeamHypothesis> createFallbackHypotheses(List<BeamHypothesis> current, char rawChar) {
        List<BeamHypothesis> fallback = new ArrayList<>(current.size());
        for (BeamHypothesis hyp : current) {
            fallback.add(new BeamHypothesis(hyp.word + rawChar, -1, hyp.spatialLogScore, hyp.totalScore));
        }
        return fallback;
    }

    private void pruneAndPushState(List<BeamHypothesis> next) {
        Collections.sort(next);
        if (next.size() > K) {
            next = new ArrayList<>(next.subList(0, K));
        }
        states.push(next);
    }

    public void onBackspace() {
        if (states.size() > 1) {
            states.pop();
        }
    }

    public List<CharSequence> getSuggestions(String typedWord, int limit, String prevWord) {
        if (typedWord == null || typedWord.isEmpty() || states.size() <= 1 || dictionary == null) {
            return Collections.emptyList();
        }
        List<BeamHypothesis> current = states.peek();
        if (current == null || current.isEmpty() || current.get(0).word.isEmpty()) {
            return Collections.emptyList();
        }
        // If the decoder hypothesis length diverges significantly from the typed word, don't supply stale decoder suggestions
        if (Math.abs(current.get(0).word.length() - typedWord.length()) > 1) {
            return Collections.emptyList();
        }
        List<CharSequence> res = new ArrayList<>();
        collectTerminalSuggestions(current, typedWord, res);
        collectPrefixSuggestions(current, typedWord, limit, res);
        return res;
    }

    private void collectTerminalSuggestions(List<BeamHypothesis> current, String typedWord, List<CharSequence> res) {
        for (BeamHypothesis hyp : current) {
            if (hyp.nodeOffset > 0 && dictionary.isTerminal(hyp.nodeOffset)) {
                addSuggestionIfAbsent(res, dictionary.getNodeWord(hyp.nodeOffset), typedWord);
            }
        }
    }

    private void collectPrefixSuggestions(List<BeamHypothesis> current, String typedWord, int limit, List<CharSequence> res) {
        if (!shouldQueryPrefixSuggestions(current, limit, res.size())) {
            return;
        }
        List<CharSequence> prefixes = dictionary.getPrefixSuggestions(current.get(0).word, limit);
        for (CharSequence p : prefixes) {
            if (res.size() >= limit) {
                break;
            }
            addSuggestionIfAbsent(res, p.toString(), typedWord);
        }
    }

    private boolean shouldQueryPrefixSuggestions(List<BeamHypothesis> current, int limit, int currentSize) {
        return currentSize < limit && !current.isEmpty() && current.get(0).nodeOffset > 0 && !current.get(0).word.isEmpty();
    }

    private void addSuggestionIfAbsent(List<CharSequence> res, String word, String typedWord) {
        if (word != null && !containsIgnoreCase(res, word)) {
            res.add(StringUtils.applyCasing(typedWord, word));
        }
    }
    
    private boolean containsIgnoreCase(List<CharSequence> list, String word) {
        for (CharSequence seq : list) {
            if (seq.toString().equalsIgnoreCase(word)) return true;
        }
        return false;
    }

    public String getBestCorrection(String typedWord, float threshold, String prevWord) {
        if (typedWord == null || typedWord.isEmpty() || states.size() <= 1 || dictionary == null) return null;
        List<BeamHypothesis> current = states.peek();
        if (current == null || current.isEmpty() || current.get(0).word.isEmpty()) return null;
        
        BeamHypothesis best = current.get(0);
        if (best.totalScore < threshold) {
            return null;
        }
        if (best.nodeOffset > 0 && dictionary.isTerminal(best.nodeOffset)) {
            String word = dictionary.getNodeWord(best.nodeOffset);
            if (word != null && !word.equalsIgnoreCase(typedWord)) {
                return StringUtils.applyCasing(typedWord, word);
            }
        }
        return null;
    }
}
