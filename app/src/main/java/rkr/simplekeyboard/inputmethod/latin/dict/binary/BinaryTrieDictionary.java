package rkr.simplekeyboard.inputmethod.latin.dict.binary;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
        char targetChar = Character.toLowerCase(target.charAt(targetIndex));
        
        int bestNode = -1;
        int maxFreq = -1;
        
        int childCount = buffer.get(node + 4) & 0xFF;
        int childrenOffset = buffer.getInt(node + 8);
        
        for (int i = 0; i < childCount; i++) {
            int childNode = childrenOffset + i * 16;
            char c = (char) (buffer.getShort(childNode) & 0xFFFF);
            char lowerChildChar = Character.toLowerCase(c);
            
            if (removeAccents(lowerChildChar) == removeAccents(targetChar)) {
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

    public List<CharSequence> getPrefixSuggestions(String prefix, int limit) {
        List<CharSequence> result = new ArrayList<>();
        int node = getNodeForWord(prefix);
        if (node > 0) {
            collectSuggestions(node, result, limit);
        }
        return result;
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
        int node = rootOffset;
        for (int i = 0; i < word.length(); i++) {
            node = getChildNode(node, word.charAt(i));
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
}
