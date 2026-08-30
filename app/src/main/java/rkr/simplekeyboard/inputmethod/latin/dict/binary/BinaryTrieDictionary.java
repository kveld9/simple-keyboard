package rkr.simplekeyboard.inputmethod.latin.dict.binary;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import rkr.simplekeyboard.inputmethod.latin.common.StringUtils;

public class BinaryTrieDictionary {
    private final ByteBuffer buffer;
    private final int rootOffset;

    public BinaryTrieDictionary(ByteBuffer buffer) {
        this.buffer = buffer;
        this.buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        int magic = this.buffer.getInt(0);
        if (magic != 0x42444b53) {
            throw new IllegalArgumentException("Invalid magic header: " + Integer.toHexString(magic));
        }
        
        int version = this.buffer.getInt(4);
        if (version != 1) {
            throw new IllegalArgumentException("Unsupported version: " + version);
        }
        
        this.rootOffset = this.buffer.getInt(12);
    }

    public int getWordFrequency(String word) {
        int node = getNodeForWord(word);
        if (node > 0 && isTerminal(node)) {
            return getNodeFrequency(node);
        }
        return -1;
    }

    public boolean containsWord(String word) {
        int node = getNodeForWord(word);
        return node > 0 && isTerminal(node);
    }

    public String getCanonicalWord(String unaccentedWord) {
        int bestNode = dfsUnaccentedMatch(rootOffset, unaccentedWord, 0);
        if (bestNode > 0 && isTerminal(bestNode)) {
            return getNodeWord(bestNode);
        }
        return null;
    }
    
    private int dfsUnaccentedMatch(int node, String target, int targetIndex) {
        if (targetIndex == target.length()) {
            return isTerminal(node) ? node : -1;
        }
        final char targetChar = StringUtils.foldChar(target.charAt(targetIndex));
        
        int bestNode = -1;
        int maxFreq = -1;
        
        int childCount = buffer.get(node + 4) & 0xFF;
        int childrenOffset = buffer.getInt(node + 8);
        
        for (int i = 0; i < childCount; i++) {
            int childNode = childrenOffset + i * 16;
            char c = (char) (buffer.getShort(childNode) & 0xFFFF);
            
            if (StringUtils.foldChar(c) == targetChar) {
                int result = dfsUnaccentedMatch(childNode, target, targetIndex + 1);
                if (result > 0) {
                    int freq = getNodeFrequency(result);
                    if (freq > maxFreq) {
                        maxFreq = freq;
                        bestNode = result;
                    }
                }
            }
        }
        return bestNode;
    }

    public List<CharSequence> getPrefixSuggestions(String prefix, int limit) {
        List<CharSequence> result = new ArrayList<>();
        if (prefix == null || prefix.isEmpty() || limit <= 0) return result;
        collectPrefixMatches(rootOffset, prefix, 0, result, limit);
        return result;
    }

    private void collectPrefixMatches(int nodeOffset, String prefix, int prefixIdx, List<CharSequence> result, int limit) {
        if (nodeOffset <= 0 || result.size() >= limit) return;
        if (prefixIdx == prefix.length()) {
            collectSuggestions(nodeOffset, result, limit);
            return;
        }

        char targetChar = StringUtils.foldChar(prefix.charAt(prefixIdx));
        int childCount = buffer.get(nodeOffset + 4) & 0xFF;
        if (childCount == 0) return;
        int childrenOffset = buffer.getInt(nodeOffset + 8);

        for (int i = 0; i < childCount; i++) {
            if (result.size() >= limit) break;
            int childNode = childrenOffset + i * 16;
            char c = (char) (buffer.getShort(childNode) & 0xFFFF);
            if (StringUtils.foldChar(c) == targetChar) {
                collectPrefixMatches(childNode, prefix, prefixIdx + 1, result, limit);
            }
        }
    }
    
    private void collectSuggestions(int node, List<CharSequence> result, int limit) {
        if (result.size() >= limit) return;
        if (isTerminal(node)) {
            result.add(getNodeWord(node));
        }
        int childCount = buffer.get(node + 4) & 0xFF;
        int childrenOffset = buffer.getInt(node + 8);
        for (int i = 0; i < childCount; i++) {
            if (result.size() >= limit) break;
            collectSuggestions(childrenOffset + i * 16, result, limit);
        }
    }

    private int getNodeForWord(String word) {
        if (word == null || word.isEmpty()) return -1;
        int node = rootOffset;
        for (int i = 0; i < word.length(); i++) {
            char c = Character.toLowerCase(word.charAt(i));
            node = getChildNode(node, c);
            if (node <= 0) return -1;
        }
        return node;
    }

    public int getRootNode() {
        return rootOffset;
    }

    public int getChildNode(int nodeOffset, char c) {
        int childCount = buffer.get(nodeOffset + 4) & 0xFF;
        if (childCount == 0) return -1;
        int childrenOffset = buffer.getInt(nodeOffset + 8);
        
        int left = 0;
        int right = childCount - 1;
        while (left <= right) {
            int mid = (left + right) >>> 1;
            int childNode = childrenOffset + mid * 16;
            char midChar = (char) (buffer.getShort(childNode) & 0xFFFF);
            if (midChar < c) {
                left = mid + 1;
            } else if (midChar > c) {
                right = mid - 1;
            } else {
                return childNode;
            }
        }
        return -1;
    }

    public boolean isTerminal(int nodeOffset) {
        byte flags = buffer.get(nodeOffset + 2);
        return (flags & 1) != 0;
    }

    public int getNodeFrequency(int nodeOffset) {
        return buffer.get(nodeOffset + 3) & 0xFF;
    }

    public String getNodeWord(int nodeOffset) {
        if (!isTerminal(nodeOffset)) return null;
        int wordOffset = buffer.getInt(nodeOffset + 12);
        if (wordOffset == 0) return null;
        
        int endOffset = wordOffset;
        while (buffer.get(endOffset) != 0) {
            endOffset++;
        }
        byte[] bytes = new byte[endOffset - wordOffset];
        int oldPos = buffer.position();
        buffer.position(wordOffset);
        buffer.get(bytes);
        buffer.position(oldPos);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public int getChildren(int nodeOffset, char[] outChars, int[] outOffsets) {
        int childCount = buffer.get(nodeOffset + 4) & 0xFF;
        if (childCount == 0) return 0;
        int childrenOffset = buffer.getInt(nodeOffset + 8);
        for (int i = 0; i < childCount && i < outChars.length; i++) {
            int childNode = childrenOffset + i * 16;
            outChars[i] = (char) (buffer.getShort(childNode) & 0xFFFF);
            outOffsets[i] = childNode;
        }
        return Math.min(childCount, outChars.length);
    }

    @FunctionalInterface
    public interface WordConsumer {
        void accept(String word, int frequency);
    }

    public void searchFuzzy(int nodeOffset, StringBuilder currentPath, String target, int targetIdx, int remainingDistance, List<rkr.simplekeyboard.inputmethod.latin.dict.PrefixDictionary.ScoredWord> candidates) {
        if (remainingDistance < 0 || candidates.size() >= 40) {
            return;
        }

        if (targetIdx == target.length() && isTerminal(nodeOffset)) {
            String word = getNodeWord(nodeOffset);
            if (word != null) {
                int freq = getNodeFrequency(nodeOffset);
                candidates.add(new rkr.simplekeyboard.inputmethod.latin.dict.PrefixDictionary.ScoredWord(word, freq));
            }
        }

        // 1. Deletion from target (extra character typed by user)
        if (targetIdx < target.length() && remainingDistance > 0) {
            searchFuzzy(nodeOffset, currentPath, target, targetIdx + 1, remainingDistance - 1, candidates);
        }

        int childCount = buffer.get(nodeOffset + 4) & 0xFF;
        if (childCount == 0) return;
        int childrenOffset = buffer.getInt(nodeOffset + 8);

        for (int i = 0; i < childCount; i++) {
            if (candidates.size() >= 40) break;
            int childNode = childrenOffset + i * 16;
            char c = (char) (buffer.getShort(childNode) & 0xFFFF);
            char normC = StringUtils.foldChar(c);

            int cost = 1;
            int nextTargetIdx = targetIdx;

            if (targetIdx < target.length()) {
                char t = StringUtils.foldChar(target.charAt(targetIdx));
                if (normC == t) {
                    cost = 0;
                    nextTargetIdx = targetIdx + 1;
                } else if (remainingDistance > 0) {
                    nextTargetIdx = targetIdx + 1;
                } else {
                    continue;
                }
            } else if (remainingDistance > 0) {
                // Insertion (missing character)
                nextTargetIdx = targetIdx;
            } else {
                continue;
            }

            if (remainingDistance >= cost) {
                currentPath.append(c);
                searchFuzzy(childNode, currentPath, target, nextTargetIdx, remainingDistance - cost, candidates);
                currentPath.setLength(currentPath.length() - 1);
            }
        }
    }

    public void forEachWord(WordConsumer consumer) {
        if (rootOffset <= 0 || consumer == null) return;
        dfsTraverse(rootOffset, new StringBuilder(), consumer);
    }

    private void dfsTraverse(int nodeOffset, StringBuilder sb, WordConsumer consumer) {
        if (isTerminal(nodeOffset)) {
            consumer.accept(sb.toString(), getNodeFrequency(nodeOffset));
        }
        int childCount = buffer.get(nodeOffset + 4) & 0xFF;
        if (childCount == 0) return;
        int childrenOffset = buffer.getInt(nodeOffset + 8);
        for (int i = 0; i < childCount; i++) {
            int childNode = childrenOffset + i * 16;
            char c = (char) (buffer.getShort(childNode) & 0xFFFF);
            sb.append(c);
            dfsTraverse(childNode, sb, consumer);
            sb.setLength(sb.length() - 1);
        }
    }
}
