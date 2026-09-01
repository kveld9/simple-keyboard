package rkr.simplekeyboard.inputmethod.latin.dict.binary;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import rkr.simplekeyboard.inputmethod.latin.common.StringUtils;

public class BinaryTrieDictionary {
    private final ByteBuffer buffer;
    private final int version;
    private final int wordCount;
    private final int rootOffset;
    private final int bigramCount;
    private final int bigramTableOffset;
    private final int stringPoolOffset;
    private final int terminalTableOffset;
    private final int nodeSize;

    public BinaryTrieDictionary(ByteBuffer buffer) {
        if (buffer == null || buffer.capacity() < 16) {
            throw new IllegalArgumentException("Invalid or too small buffer");
        }
        this.buffer = buffer;
        this.buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        int magic = this.buffer.getInt(0);
        if (magic != 0x42444b53) {
            throw new IllegalArgumentException("Invalid magic header: " + Integer.toHexString(magic));
        }
        
        this.version = this.buffer.getInt(4);
        if (this.version != 1 && this.version != 2 && this.version != 3) {
            throw new IllegalArgumentException("Unsupported version: " + this.version);
        }
        
        this.nodeSize = (this.version >= 3) ? 8 : 16;
        this.wordCount = this.buffer.getInt(8);
        this.rootOffset = this.buffer.getInt(12);
        final int minHeader = (this.version >= 2) ? 32 : 16;
        if (this.wordCount > 0 && (this.rootOffset < minHeader || this.rootOffset >= this.buffer.capacity())) {
            throw new IllegalArgumentException("Invalid root offset: " + this.rootOffset);
        }

        if (this.version >= 2) {
            if (this.buffer.capacity() < 32) {
                throw new IllegalArgumentException("Buffer too small for v2/v3 header: " + this.buffer.capacity());
            }
            this.bigramCount = this.buffer.getInt(16);
            this.bigramTableOffset = this.buffer.getInt(20);
            this.stringPoolOffset = this.buffer.getInt(24);
            this.terminalTableOffset = (this.version >= 3) ? this.buffer.getInt(28) : 0;

            if (this.bigramCount < 0) {
                throw new IllegalArgumentException("Invalid bigram count: " + this.bigramCount);
            }
            if (this.bigramCount > 0 && (this.bigramTableOffset < 32 || this.bigramTableOffset >= this.buffer.capacity())) {
                throw new IllegalArgumentException("Invalid bigram table offset: " + this.bigramTableOffset);
            }
        } else {
            this.bigramCount = 0;
            this.bigramTableOffset = 0;
            this.stringPoolOffset = 0;
            this.terminalTableOffset = 0;
        }
    }

    public int getVersion() {
        return version;
    }

    public int getWordCount() {
        return wordCount;
    }

    public int getBigramCount() {
        return bigramCount;
    }

    public boolean validateStructure() {
        if (wordCount == 0) {
            return true;
        }
        final int capacity = buffer.capacity();
        final int minHeader = (version >= 2) ? 32 : 16;
        if (capacity < minHeader || rootOffset < minHeader || rootOffset + nodeSize > capacity) {
            return false;
        }

        if (version >= 2 && bigramCount > 0) {
            if (bigramTableOffset < minHeader || (long) bigramTableOffset + ((long) bigramCount * 12) > capacity) {
                return false;
            }
            if (stringPoolOffset < minHeader || stringPoolOffset >= capacity) {
                return false;
            }
            int prevW1 = -1;
            for (int i = 0; i < bigramCount; i++) {
                int entryOffset = bigramTableOffset + i * 12;
                int w1 = buffer.getInt(entryOffset);
                int w2 = buffer.getInt(entryOffset + 4);
                int freq = buffer.getShort(entryOffset + 8) & 0xFFFF;
                if (w1 < minHeader || w1 >= capacity || w2 < minHeader || w2 >= capacity) {
                    return false;
                }
                if (freq <= 0 || freq > 255) {
                    return false;
                }
                if (w1 < prevW1) {
                    return false; // Not sorted by word1Offset
                }
                prevW1 = w1;
            }
        }

        if (version >= 3 && terminalTableOffset > 0) {
            if (terminalTableOffset < minHeader || (long) terminalTableOffset + ((long) wordCount * 8) > capacity) {
                return false;
            }
        }

        // BitSet indexed by node slot (node / nodeSize) -> zero boxing
        final java.util.BitSet visitedSlots = new java.util.BitSet(capacity / nodeSize + 1);
        final java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        queue.add(rootOffset);
        int validatedWords = 0;

        while (!queue.isEmpty()) {
            final int node = queue.poll();
            if (node < minHeader || (long) node + nodeSize > capacity) {
                return false;
            }
            final int slot = node / nodeSize;
            if (visitedSlots.get(slot)) {
                return false; // Cycle detected
            }
            visitedSlots.set(slot);

            final byte flags = buffer.get(node + 2);
            final int childCount = buffer.get(node + 4) & 0xFF;
            final int childrenOffset = getChildrenOffset(node);
            final int wordOffset = getNodeWordOffset(node);

            if ((flags & 1) != 0) { // Terminal
                if (wordOffset < minHeader || wordOffset >= capacity) {
                    return false;
                }
                // Verify null-terminated UTF-8 string within bounds
                int strEnd = wordOffset;
                while (strEnd < capacity && buffer.get(strEnd) != 0 && (strEnd - wordOffset) <= 64) {
                    strEnd++;
                }
                if (strEnd >= capacity || buffer.get(strEnd) != 0) {
                    return false; // String not null-terminated within bounds
                }
                validatedWords++;
            }

            if ((flags & 2) != 0) { // Has children
                final long childrenEnd = (long) childrenOffset + ((long) childCount * nodeSize);
                if (childCount <= 0 || childrenOffset < minHeader || childrenEnd > capacity) {
                    return false;
                }
                for (int i = 0; i < childCount; i++) {
                    final long childNodeLong = (long) childrenOffset + ((long) i * nodeSize);
                    if (childNodeLong < minHeader || childNodeLong + nodeSize > capacity) {
                        return false;
                    }
                    queue.add((int) childNodeLong);
                }
            }
        }
        return validatedWords > 0;
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
        int childrenOffset = getChildrenOffset(node);
        
        for (int i = 0; i < childCount; i++) {
            int childNode = childrenOffset + i * nodeSize;
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

    public void getPrefixSuggestions(String prefix, int limit, List<CharSequence> result) {
        if (prefix == null || prefix.isEmpty() || limit <= 0 || result == null) return;
        collectPrefixMatches(rootOffset, prefix, 0, result, limit);
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
        int childrenOffset = getChildrenOffset(nodeOffset);

        for (int i = 0; i < childCount; i++) {
            if (result.size() >= limit) break;
            int childNode = childrenOffset + i * nodeSize;
            char c = (char) (buffer.getShort(childNode) & 0xFFFF);
            if (StringUtils.foldChar(c) == targetChar) {
                collectPrefixMatches(childNode, prefix, prefixIdx + 1, result, limit);
            }
        }
    }
    
    private void collectSuggestions(int node, List<CharSequence> result, int limit) {
        if (node <= 0 || result.size() >= limit) return;

        final int maxQueue = 512;
        final int[] queue = new int[maxQueue];
        int head = 0;
        int tail = 0;

        queue[tail++] = node;

        while (head < tail && result.size() < limit) {
            int current = queue[head++];
            if (isTerminal(current)) {
                final String word = getNodeWord(current);
                if (word != null) {
                    result.add(word);
                    if (result.size() >= limit) {
                        break;
                    }
                }
            }
            int childCount = buffer.get(current + 4) & 0xFF;
            if (childCount > 0) {
                int childrenOffset = getChildrenOffset(current);
                for (int i = 0; i < childCount; i++) {
                    if (tail < maxQueue) {
                        queue[tail++] = childrenOffset + i * nodeSize;
                    }
                }
            }
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
        int childrenOffset = getChildrenOffset(nodeOffset);
        
        if (childCount <= 16) {
            for (int i = 0; i < childCount; i++) {
                int childNode = childrenOffset + i * nodeSize;
                char childChar = (char) (buffer.getShort(childNode) & 0xFFFF);
                if (childChar == c) return childNode;
                if (childChar > c) return -1;
            }
            return -1;
        }

        int left = 0;
        int right = childCount - 1;
        while (left <= right) {
            int mid = (left + right) >>> 1;
            int childNode = childrenOffset + mid * nodeSize;
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
        int wordOffset = getNodeWordOffset(nodeOffset);
        if (wordOffset == 0) return null;
        return getWordAtOffset(wordOffset);
    }

    public int getChildrenOffset(int nodeOffset) {
        if (version >= 3) {
            int slot = (buffer.get(nodeOffset + 5) & 0xFF)
                    | ((buffer.get(nodeOffset + 6) & 0xFF) << 8)
                    | ((buffer.get(nodeOffset + 7) & 0xFF) << 16);
            return slot * nodeSize;
        } else {
            return buffer.getInt(nodeOffset + 8);
        }
    }

    public int getNodeWordOffset(int nodeOffset) {
        if (version >= 3) {
            return findWordOffsetInTerminalTable(nodeOffset);
        } else {
            return buffer.getInt(nodeOffset + 12);
        }
    }

    private int findWordOffsetInTerminalTable(int targetNodeOffset) {
        if (terminalTableOffset <= 0 || wordCount == 0) {
            return 0;
        }
        int low = 0;
        int high = wordCount - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int entryOffset = terminalTableOffset + mid * 8;
            int nodeOff = buffer.getInt(entryOffset);
            if (nodeOff == targetNodeOffset) {
                return buffer.getInt(entryOffset + 4);
            } else if (nodeOff < targetNodeOffset) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return 0;
    }

    private static final ThreadLocal<byte[]> sScratchBytes = new ThreadLocal<byte[]>() {
        @Override
        protected byte[] initialValue() {
            return new byte[64];
        }
    };

    public String getWordAtOffset(int wordOffset) {
        final int minOffset = (version >= 2) ? 32 : 16;
        if (wordOffset < minOffset || wordOffset >= buffer.capacity()) return null;
        
        int endOffset = wordOffset;
        while (endOffset < buffer.capacity() && buffer.get(endOffset) != 0) {
            endOffset++;
        }
        if (endOffset >= buffer.capacity()) return null;

        int len = endOffset - wordOffset;
        if (len == 0) return "";
        byte[] bytes = sScratchBytes.get();
        if (bytes.length < len) {
            bytes = new byte[Math.max(bytes.length * 2, len)];
            sScratchBytes.set(bytes);
        }
        int oldPos = buffer.position();
        buffer.position(wordOffset);
        buffer.get(bytes, 0, len);
        buffer.position(oldPos);
        return new String(bytes, 0, len, StandardCharsets.UTF_8);
    }

    public int getChildren(int nodeOffset, char[] outChars, int[] outOffsets) {
        int childCount = buffer.get(nodeOffset + 4) & 0xFF;
        if (childCount == 0) return 0;
        int childrenOffset = getChildrenOffset(nodeOffset);
        for (int i = 0; i < childCount && i < outChars.length; i++) {
            int childNode = childrenOffset + i * nodeSize;
            outChars[i] = (char) (buffer.getShort(childNode) & 0xFFFF);
            outOffsets[i] = childNode;
        }
        return Math.min(childCount, outChars.length);
    }

    private int findFirstBigramIndex(int targetW1Offset) {
        if (bigramCount == 0 || bigramTableOffset <= 0) {
            return -1;
        }
        int low = 0;
        int high = bigramCount - 1;
        int result = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midW1Offset = buffer.getInt(bigramTableOffset + mid * 12);
            if (midW1Offset == targetW1Offset) {
                result = mid;
                high = mid - 1; // Keep searching left for the first match
            } else if (midW1Offset < targetW1Offset) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }

    public int getBigramFrequency(final String word1, final String word2) {
        if (word1 == null || word2 == null || word1.isEmpty() || word2.isEmpty() || bigramCount == 0) {
            return 0;
        }
        int node1 = getNodeForWord(word1);
        if (node1 <= 0 || !isTerminal(node1)) {
            return 0;
        }
        int node2 = getNodeForWord(word2);
        if (node2 <= 0 || !isTerminal(node2)) {
            return 0;
        }
        int w1Offset = getNodeWordOffset(node1);
        int w2Offset = getNodeWordOffset(node2);
        if (w1Offset == 0 || w2Offset == 0) {
            return 0;
        }

        int firstIdx = findFirstBigramIndex(w1Offset);
        if (firstIdx < 0) {
            return 0;
        }

        for (int i = firstIdx; i < bigramCount; i++) {
            int entryOffset = bigramTableOffset + i * 12;
            int currentW1Offset = buffer.getInt(entryOffset);
            if (currentW1Offset != w1Offset) {
                break;
            }
            int currentW2Offset = buffer.getInt(entryOffset + 4);
            if (currentW2Offset == w2Offset) {
                return buffer.getShort(entryOffset + 8) & 0xFFFF;
            }
        }
        return 0;
    }

    public int getNextWordPredictions(final String prevWord, final int limit, final List<CharSequence> out) {
        if (prevWord == null || prevWord.isEmpty() || limit <= 0 || out == null || bigramCount == 0) {
            return 0;
        }
        int node = getNodeForWord(prevWord);
        if (node <= 0 || !isTerminal(node)) {
            return 0;
        }
        int w1Offset = getNodeWordOffset(node);
        if (w1Offset == 0) {
            return 0;
        }

        int firstIdx = findFirstBigramIndex(w1Offset);
        if (firstIdx < 0) {
            return 0;
        }

        int count = 0;
        for (int i = firstIdx; i < bigramCount && count < limit; i++) {
            int entryOffset = bigramTableOffset + i * 12;
            int currentW1Offset = buffer.getInt(entryOffset);
            if (currentW1Offset != w1Offset) {
                break;
            }
            int w2Offset = buffer.getInt(entryOffset + 4);
            String word2 = getWordAtOffset(w2Offset);
            if (word2 != null && !word2.isEmpty()) {
                out.add(word2);
                count++;
            }
        }
        return count;
    }

    public List<CharSequence> getNextWordPredictions(final String prevWord, final int limit) {
        final List<CharSequence> result = new ArrayList<>(Math.min(Math.max(limit, 0), 16));
        if (limit > 0) {
            getNextWordPredictions(prevWord, limit, result);
        }
        return result;
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
            final String word = getNodeWord(nodeOffset);
            final int freq = getNodeFrequency(nodeOffset);
            candidates.add(new rkr.simplekeyboard.inputmethod.latin.dict.PrefixDictionary.ScoredWord(word, freq));
            return;
        }

        // 1. Deletion from target (extra character typed by user, Cost 1)
        if (targetIdx < target.length() && remainingDistance > 0) {
            searchFuzzy(nodeOffset, currentPath, target, targetIdx + 1, remainingDistance - 1, candidates);
        }

        final int childCount = buffer.get(nodeOffset + 4) & 0xFF;
        if (childCount == 0 || candidates.size() >= 40) {
            return;
        }
        final int childrenOffset = getChildrenOffset(nodeOffset);

        // 2. Exact match on children (Cost 0)
        final char targetChar = (targetIdx < target.length()) ? StringUtils.foldChar(target.charAt(targetIdx)) : '\0';
        if (targetIdx < target.length()) {
            for (int i = 0; i < childCount; i++) {
                final int childNode = childrenOffset + i * nodeSize;
                final char c = (char) (buffer.getShort(childNode) & 0xFFFF);
                if (StringUtils.foldChar(c) == targetChar) {
                    currentPath.append(c);
                    searchFuzzy(childNode, currentPath, target, targetIdx + 1, remainingDistance, candidates);
                    currentPath.setLength(currentPath.length() - 1);
                }
            }
        }

        if (remainingDistance <= 0 || candidates.size() >= 40) {
            return;
        }

        final char targetNextChar = (targetIdx + 1 < target.length()) ? StringUtils.foldChar(target.charAt(targetIdx + 1)) : '\0';

        // 3. Substitutions, Insertions, and Transpositions (Cost 1)
        for (int i = 0; i < childCount; i++) {
            if (candidates.size() >= 40) {
                break;
            }
            final int childNode = childrenOffset + i * nodeSize;
            final char c = (char) (buffer.getShort(childNode) & 0xFFFF);
            final char foldedC = StringUtils.foldChar(c);
            final boolean isExactMatch = (targetIdx < target.length() && foldedC == targetChar);

            currentPath.append(c);

            // 3a. Substitution (advance targetIdx, only for non-exact children)
            if (targetIdx < target.length() && !isExactMatch) {
                searchFuzzy(childNode, currentPath, target, targetIdx + 1, remainingDistance - 1, candidates);
            }

            // 3b. Insertion (keep targetIdx)
            if (candidates.size() < 40) {
                searchFuzzy(childNode, currentPath, target, targetIdx, remainingDistance - 1, candidates);
            }

            // 3c. Transposition (adjacent swap: child matches target[targetIdx + 1], grandChild matches target[targetIdx])
            if (candidates.size() < 40 && targetIdx + 1 < target.length() && foldedC == targetNextChar) {
                final int grandChildCount = buffer.get(childNode + 4) & 0xFF;
                if (grandChildCount > 0) {
                    final int grandChildrenOffset = getChildrenOffset(childNode);
                    for (int j = 0; j < grandChildCount; j++) {
                        if (candidates.size() >= 40) {
                            break;
                        }
                        final int grandChildNode = grandChildrenOffset + j * nodeSize;
                        final char grandC = (char) (buffer.getShort(grandChildNode) & 0xFFFF);
                        if (StringUtils.foldChar(grandC) == targetChar) {
                            currentPath.append(grandC);
                            searchFuzzy(grandChildNode, currentPath, target, targetIdx + 2, remainingDistance - 1, candidates);
                            currentPath.setLength(currentPath.length() - 1);
                        }
                    }
                }
            }

            currentPath.setLength(currentPath.length() - 1);
        }
    }

    private static final ThreadLocal<StringBuilder> sDfsStringBuilder = new ThreadLocal<StringBuilder>() {
        @Override
        protected StringBuilder initialValue() {
            return new StringBuilder(64);
        }
    };

    public void forEachWord(WordConsumer consumer) {
        if (rootOffset <= 0 || consumer == null) return;
        StringBuilder sb = sDfsStringBuilder.get();
        sb.setLength(0);
        dfsTraverse(rootOffset, sb, consumer);
    }

    private void dfsTraverse(int nodeOffset, StringBuilder sb, WordConsumer consumer) {
        if (isTerminal(nodeOffset)) {
            consumer.accept(sb.toString(), getNodeFrequency(nodeOffset));
        }
        int childCount = buffer.get(nodeOffset + 4) & 0xFF;
        if (childCount == 0) return;
        int childrenOffset = getChildrenOffset(nodeOffset);
        for (int i = 0; i < childCount; i++) {
            int childNode = childrenOffset + i * nodeSize;
            char c = (char) (buffer.getShort(childNode) & 0xFFFF);
            sb.append(c);
            dfsTraverse(childNode, sb, consumer);
            sb.setLength(sb.length() - 1);
        }
    }
}
