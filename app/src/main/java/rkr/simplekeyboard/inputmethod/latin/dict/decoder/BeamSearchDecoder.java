package rkr.simplekeyboard.inputmethod.latin.dict.decoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

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
        List<BeamHypothesis> current = states.isEmpty() ? new ArrayList<BeamHypothesis>() : states.peek();
        List<BeamHypothesis> next = new ArrayList<>();

        List<SpatialCandidate> candidates = spatialModel != null ? spatialModel.getCandidatesForTouch(x, y, rawChar) : new ArrayList<SpatialCandidate>();
        if (candidates.isEmpty()) {
            candidates.add(new SpatialCandidate(rawChar, 1.0f, 0.0f));
        }

        if (dictionary != null) {
            for (BeamHypothesis hyp : current) {
                if (hyp.nodeOffset <= 0) continue;
                
                char[] childrenChars = new char[256];
                int[] childrenOffsets = new int[256];
                int count = dictionary.getChildren(hyp.nodeOffset, childrenChars, childrenOffsets);

                for (int i = 0; i < count; i++) {
                    char childChar = childrenChars[i];
                    int childNode = childrenOffsets[i];
                    char lowerChildChar = Character.toLowerCase(childChar);

                    for (SpatialCandidate cand : candidates) {
                        if (removeAccents(lowerChildChar) == removeAccents(Character.toLowerCase(cand.codePoint))) {
                            float freqLog = dictionary.getNodeFrequency(childNode) > 0 ? (float) Math.log(dictionary.getNodeFrequency(childNode)) : 0.0f;
                            float newSpatialLogScore = hyp.spatialLogScore + cand.logProb;
                            float newTotalScore = newSpatialLogScore + LAMBDA * freqLog;
                            next.add(new BeamHypothesis(hyp.word + childChar, childNode, newSpatialLogScore, newTotalScore));
                        }
                    }
                }
            }
        }

        if (next.isEmpty()) {
            for (BeamHypothesis hyp : current) {
                next.add(new BeamHypothesis(hyp.word + rawChar, -1, hyp.spatialLogScore, hyp.totalScore));
            }
        }

        Collections.sort(next);
        if (next.size() > K) {
            next = new ArrayList<>(next.subList(0, K));
        }
        states.push(next);
    }

    private char removeAccents(char c) {
        switch (c) {
            case 'á': case 'à': case 'ä': case 'â': return 'a';
            case 'é': case 'è': case 'ë': case 'ê': return 'e';
            case 'í': case 'ì': case 'ï': case 'î': return 'i';
            case 'ó': case 'ò': case 'ö': case 'ô': return 'o';
            case 'ú': case 'ù': case 'ü': case 'û': return 'u';
            case 'ñ': return 'n';
            case 'ç': return 'c';
            default: return c;
        }
    }

    public void onBackspace() {
        if (states.size() > 1) {
            states.pop();
        }
    }

    public List<CharSequence> getSuggestions(String typedWord, int limit, String prevWord) {
        List<CharSequence> res = new ArrayList<>();
        if (states.isEmpty() || dictionary == null) return res;
        List<BeamHypothesis> current = states.peek();
        
        for (BeamHypothesis hyp : current) {
            if (hyp.nodeOffset > 0 && dictionary.isTerminal(hyp.nodeOffset)) {
                String word = dictionary.getNodeWord(hyp.nodeOffset);
                if (word != null && !containsIgnoreCase(res, word)) {
                    res.add(matchCase(word, typedWord));
                }
            }
        }
        
        if (res.size() < limit && !current.isEmpty() && current.get(0).nodeOffset > 0) {
            List<CharSequence> prefixes = dictionary.getPrefixSuggestions(current.get(0).word, limit);
            for (CharSequence p : prefixes) {
                if (res.size() >= limit) break;
                if (!containsIgnoreCase(res, p.toString())) {
                    res.add(matchCase(p.toString(), typedWord));
                }
            }
        }
        return res;
    }
    
    private boolean containsIgnoreCase(List<CharSequence> list, String word) {
        for (CharSequence seq : list) {
            if (seq.toString().equalsIgnoreCase(word)) return true;
        }
        return false;
    }
    
    private String matchCase(String word, String typedWord) {
        if (typedWord.isEmpty() || word.isEmpty()) return word;
        boolean allUpper = true;
        boolean firstUpper = Character.isUpperCase(typedWord.charAt(0));
        for (int i = 0; i < typedWord.length(); i++) {
            if (Character.isLetter(typedWord.charAt(i)) && !Character.isUpperCase(typedWord.charAt(i))) {
                allUpper = false;
                break;
            }
        }
        if (allUpper && typedWord.length() > 1) return word.toUpperCase();
        if (firstUpper) {
            return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase();
        }
        return word.toLowerCase();
    }

    public String getBestCorrection(String typedWord, float threshold, String prevWord) {
        if (states.isEmpty() || dictionary == null) return null;
        List<BeamHypothesis> current = states.peek();
        if (current.isEmpty()) return null;
        
        BeamHypothesis best = current.get(0);
        if (best.nodeOffset > 0 && dictionary.isTerminal(best.nodeOffset)) {
            String word = dictionary.getNodeWord(best.nodeOffset);
            if (word != null && !word.equalsIgnoreCase(typedWord)) {
                // simple delta check
                return matchCase(word, typedWord);
            }
        }
        return null;
    }
}
