package rkr.simplekeyboard.inputmethod.latin.dict.binary;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

public final class BinaryTrieCompiler {

    public static final class WordEntry {
        public final String word;
        public final int frequency;

        public WordEntry(final String word, final int frequency) {
            this.word = word;
            this.frequency = Math.min(Math.max(frequency, 1), 255);
        }
    }

    private static final class BuildNode {
        final char character;
        int frequency = 0;
        boolean isTerminal = false;
        String word = null;
        int offset = 0;
        final Map<Character, BuildNode> children = new TreeMap<>();
        List<BuildNode> childrenList = Collections.emptyList();

        BuildNode(final char character) {
            this.character = character;
        }
    }

    private BinaryTrieCompiler() {
    }

    public static void compile(final List<WordEntry> words, final File outputFile) throws IOException {
        final BuildNode root = new BuildNode('\0');

        for (final WordEntry entry : words) {
            if (entry.word == null || entry.word.isEmpty()) {
                continue;
            }
            BuildNode current = root;
            final int len = entry.word.length();
            for (int i = 0; i < len; i++) {
                final char c = entry.word.charAt(i);
                BuildNode next = current.children.get(c);
                if (next == null) {
                    next = new BuildNode(c);
                    current.children.put(c, next);
                }
                current = next;
            }
            current.isTerminal = true;
            current.frequency = entry.frequency;
            current.word = entry.word;
        }

        final ByteArrayOutputStream stringPoolStream = new ByteArrayOutputStream();
        final Map<String, Integer> wordToOffset = new HashMap<>();

        final List<BuildNode> allNodes = new ArrayList<>();
        final Queue<List<BuildNode>> queue = new LinkedList<>();

        final int headerSize = 16;
        final int nodeSize = 16;
        int currentNodeOffset = headerSize;

        final List<BuildNode> rootList = Collections.singletonList(root);
        queue.add(rootList);

        while (!queue.isEmpty()) {
            final List<BuildNode> levelNodes = queue.poll();
            if (levelNodes == null || levelNodes.isEmpty()) {
                continue;
            }
            int offset = currentNodeOffset;
            currentNodeOffset += levelNodes.size() * nodeSize;
            for (final BuildNode node : levelNodes) {
                node.offset = offset;
                offset += nodeSize;
                allNodes.add(node);

                if (!node.children.isEmpty()) {
                    final List<BuildNode> children = new ArrayList<>(node.children.values());
                    node.childrenList = children;
                    queue.add(children);
                }
            }
        }

        for (final BuildNode node : allNodes) {
            if (node.isTerminal && node.word != null && !wordToOffset.containsKey(node.word)) {
                final byte[] utf8 = node.word.getBytes(StandardCharsets.UTF_8);
                final int strOffset = stringPoolStream.size();
                wordToOffset.put(node.word, strOffset);
                stringPoolStream.write(utf8);
                stringPoolStream.write(0);
            }
        }

        final int stringPoolStart = currentNodeOffset;
        final byte[] stringPoolBytes = stringPoolStream.toByteArray();

        final ByteBuffer buffer = ByteBuffer.allocate(stringPoolStart + stringPoolBytes.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Header: Magic "SKDB" (0x42444B53), Version 1, Word Count, Root Offset
        buffer.putInt(0x42444B53);
        buffer.putInt(1);
        buffer.putInt(words.size());
        buffer.putInt(root.offset);

        // Sort nodes by offset to write sequentially
        allNodes.sort((a, b) -> Integer.compare(a.offset, b.offset));

        for (final BuildNode node : allNodes) {
            final short charVal = (short) node.character;
            byte flags = 0;
            if (node.isTerminal) flags |= 1;
            if (!node.childrenList.isEmpty()) flags |= 2;

            final byte freq = (byte) (node.frequency & 0xFF);
            final byte childCount = (byte) Math.min(node.childrenList.size(), 255);
            final int childrenOffset = !node.childrenList.isEmpty() ? node.childrenList.get(0).offset : 0;
            final int wordOffset = (node.isTerminal && node.word != null)
                    ? stringPoolStart + wordToOffset.get(node.word) : 0;

            buffer.putShort(charVal);
            buffer.put(flags);
            buffer.put(freq);
            buffer.put(childCount);
            buffer.put((byte) 0); // 3 pad bytes
            buffer.put((byte) 0);
            buffer.put((byte) 0);
            buffer.putInt(childrenOffset);
            buffer.putInt(wordOffset);
        }

        buffer.put(stringPoolBytes);

        final File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(buffer.array(), 0, buffer.position());
        }
    }
}
