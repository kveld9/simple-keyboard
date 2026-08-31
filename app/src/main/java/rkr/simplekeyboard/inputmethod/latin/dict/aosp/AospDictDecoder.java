package rkr.simplekeyboard.inputmethod.latin.dict.aosp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rkr.simplekeyboard.inputmethod.latin.dict.binary.BinaryTrieCompiler;

public final class AospDictDecoder {

    public static final int MAGIC_AOSP_V202 = 0x9BC13AFE;
    public static final int MAGIC_AOSP_V2 = 0x9BCB00FE;
    public static final int MAGIC_AOSP_V4 = 0x9BCB00FD;

    public static final class DecodedDictionary {
        public final String locale;
        public final String languageCode;
        public final int version;
        public final Map<String, String> attributes;
        public final List<BinaryTrieCompiler.WordEntry> words;

        public DecodedDictionary(final String locale, final String languageCode, final int version,
                                 final Map<String, String> attributes, final List<BinaryTrieCompiler.WordEntry> words) {
            this.locale = locale;
            this.languageCode = languageCode;
            this.version = version;
            this.attributes = attributes;
            this.words = words;
        }
    }

    private AospDictDecoder() {
    }

    public static boolean isAospDictionary(final File file) {
        if (file == null || !file.exists() || file.length() < 16) {
            return false;
        }
        try (FileInputStream fis = new FileInputStream(file);
             FileChannel channel = fis.getChannel()) {
            final ByteBuffer buffer = ByteBuffer.allocate(4);
            buffer.order(ByteOrder.BIG_ENDIAN);
            channel.read(buffer);
            buffer.flip();
            final int magic = buffer.getInt();
            return magic == MAGIC_AOSP_V202 || magic == MAGIC_AOSP_V2 || magic == MAGIC_AOSP_V4;
        } catch (Exception e) {
            return false;
        }
    }

    public static DecodedDictionary decode(final File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             FileChannel channel = fis.getChannel()) {
            final ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            buffer.order(ByteOrder.BIG_ENDIAN);
            return decode(buffer);
        }
    }

    private static final int MAX_PARSE_DEPTH = 64;
    private static final int MAX_DECODED_WORDS = 250_000;
    private static final int MAX_WORD_LENGTH = 64;

    public static DecodedDictionary decode(final ByteBuffer buffer) throws IOException {
        buffer.position(0);
        final int magic = buffer.getInt();
        if (magic != MAGIC_AOSP_V202 && magic != MAGIC_AOSP_V2 && magic != MAGIC_AOSP_V4) {
            throw new IllegalArgumentException("Unsupported AOSP dictionary magic: 0x" + Integer.toHexString(magic));
        }

        final int version = buffer.getShort() & 0xFFFF;
        buffer.getShort(); // skip options flags
        final int headerSize = buffer.getInt();
        if (headerSize < 12 || headerSize > buffer.capacity()) {
            throw new IllegalArgumentException("Invalid AOSP dictionary header size: " + headerSize);
        }

        final Map<String, String> attributes = decodeHeaderAttributes(buffer, headerSize);
        String locale = attributes.get("locale");
        if (locale == null || locale.isEmpty()) {
            final String dictId = attributes.get("dictionary");
            if (dictId != null && dictId.contains(":")) {
                locale = dictId.substring(dictId.indexOf(':') + 1);
            }
        }

        String languageCode = null;
        if (locale != null && !locale.isEmpty()) {
            languageCode = locale;
            if (languageCode.contains("_")) {
                languageCode = languageCode.substring(0, languageCode.indexOf('_'));
            }
            if (languageCode.contains("-")) {
                languageCode = languageCode.substring(0, languageCode.indexOf('-'));
            }
            languageCode = languageCode.toLowerCase(java.util.Locale.US);
        }

        final List<BinaryTrieCompiler.WordEntry> words = new ArrayList<>();
        final StringBuilder prefixBuilder = new StringBuilder(64);
        final java.util.BitSet visitedOffsets = new java.util.BitSet(buffer.capacity());
        parsePtNodeArray(buffer, headerSize, prefixBuilder, words, visitedOffsets, 0);

        return new DecodedDictionary(locale, languageCode, version, attributes, words);
    }

    private static Map<String, String> decodeHeaderAttributes(final ByteBuffer buffer, final int headerSize) {
        final Map<String, String> attributes = new HashMap<>();
        final List<String> keyValues = new ArrayList<>();
        final StringBuilder sb = new StringBuilder();

        int pos = 12;
        final int end = headerSize;
        while (pos < end && pos < buffer.capacity()) {
            final int b = buffer.get(pos) & 0xFF;
            if (b == 0x1F) { // Terminator
                keyValues.add(sb.toString());
                sb.setLength(0);
                pos++;
            } else if (b >= 0x20) {
                sb.append((char) b);
                pos++;
            } else {
                if (pos + 2 < end) {
                    final int b1 = buffer.get(pos + 1) & 0xFF;
                    final int b2 = buffer.get(pos + 2) & 0xFF;
                    final int codePoint = (b << 16) | (b1 << 8) | b2;
                    sb.appendCodePoint(codePoint);
                    pos += 3;
                } else {
                    pos++;
                }
            }
        }

        for (int i = 0; i + 1 < keyValues.size(); i += 2) {
            attributes.put(keyValues.get(i), keyValues.get(i + 1));
        }
        return attributes;
    }

    private static int readChar(final ByteBuffer buffer, final int[] posRef) {
        int pos = posRef[0];
        if (pos >= buffer.capacity()) {
            return -1;
        }
        final int b0 = buffer.get(pos) & 0xFF;
        pos++;
        if (b0 == 0x1F) {
            posRef[0] = pos;
            return -1; // Terminator
        }
        if (b0 >= 0x20) {
            posRef[0] = pos;
            return b0;
        }
        if (pos + 1 < buffer.capacity()) {
            final int b1 = buffer.get(pos) & 0xFF;
            final int b2 = buffer.get(pos + 1) & 0xFF;
            pos += 2;
            posRef[0] = pos;
            return (b0 << 16) | (b1 << 8) | b2;
        }
        posRef[0] = pos;
        return b0;
    }

    private static int readPtNodeCount(final ByteBuffer buffer, final int[] posRef) {
        int pos = posRef[0];
        if (pos >= buffer.capacity()) {
            return 0;
        }
        final int msb = buffer.get(pos) & 0xFF;
        pos++;
        if (msb <= 127) {
            posRef[0] = pos;
            return msb;
        }
        if (pos < buffer.capacity()) {
            final int lsb = buffer.get(pos) & 0xFF;
            pos++;
            posRef[0] = pos;
            return ((msb & 0x7F) << 8) | lsb;
        }
        posRef[0] = pos;
        return msb & 0x7F;
    }

    private static void parsePtNodeArray(final ByteBuffer buffer, final int groupOffset,
                                         final StringBuilder prefix,
                                         final List<BinaryTrieCompiler.WordEntry> words,
                                         final java.util.BitSet visitedOffsets,
                                         final int depth) {
        if (depth > MAX_PARSE_DEPTH || words.size() >= MAX_DECODED_WORDS) {
            return;
        }
        if (groupOffset <= 0 || groupOffset >= buffer.capacity()) {
            return;
        }
        if (visitedOffsets.get(groupOffset)) {
            return; // Cycle detected, prevent infinite loop
        }
        visitedOffsets.set(groupOffset);

        final int[] posRef = new int[]{groupOffset};
        final int count = readPtNodeCount(buffer, posRef);
        final List<int[]> childrenToVisit = new ArrayList<>();
        final List<String> childPrefixes = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            if (posRef[0] >= buffer.capacity() || words.size() >= MAX_DECODED_WORDS) {
                break;
            }
            final int flags = buffer.get(posRef[0]) & 0xFF;
            posRef[0]++;

            final int prefixLengthBefore = prefix.length();
            if ((flags & 0x20) != 0) { // FLAG_HAS_MULTIPLE_CHARS
                while (posRef[0] < buffer.capacity() && prefix.length() < MAX_WORD_LENGTH) {
                    final int c = readChar(buffer, posRef);
                    if (c == -1) {
                        break;
                    }
                    prefix.appendCodePoint(c);
                }
            } else {
                final int c = readChar(buffer, posRef);
                if (c != -1 && prefix.length() < MAX_WORD_LENGTH) {
                    prefix.appendCodePoint(c);
                }
            }

            if ((flags & 0x10) != 0) { // FLAG_IS_TERMINAL
                if (posRef[0] < buffer.capacity()) {
                    final int freq = buffer.get(posRef[0]) & 0xFF;
                    posRef[0]++;
                    if ((flags & 0x02) == 0 && prefix.length() > 0) { // not NOT_A_WORD
                        words.add(new BinaryTrieCompiler.WordEntry(prefix.toString(), freq));
                    }
                }
            }

            final int addrType = (flags & 0xC0);
            int childrenPos = -1;
            if (addrType == 0x40 && posRef[0] < buffer.capacity()) {
                final int offset = buffer.get(posRef[0]) & 0xFF;
                childrenPos = posRef[0] + offset;
                posRef[0]++;
            } else if (addrType == 0x80 && posRef[0] + 1 < buffer.capacity()) {
                final int offset = ((buffer.get(posRef[0]) & 0xFF) << 8) | (buffer.get(posRef[0] + 1) & 0xFF);
                childrenPos = posRef[0] + offset;
                posRef[0] += 2;
            } else if (addrType == 0xC0 && posRef[0] + 2 < buffer.capacity()) {
                final int offset = ((buffer.get(posRef[0]) & 0xFF) << 16)
                        | ((buffer.get(posRef[0] + 1) & 0xFF) << 8)
                        | (buffer.get(posRef[0] + 2) & 0xFF);
                childrenPos = posRef[0] + offset;
                posRef[0] += 3;
            }

            if ((flags & 0x10) != 0) {
                if ((flags & 0x08) != 0 && posRef[0] + 1 < buffer.capacity()) { // SHORTCUTS
                    final int scLen = ((buffer.get(posRef[0]) & 0xFF) << 8) | (buffer.get(posRef[0] + 1) & 0xFF);
                    posRef[0] += 2 + scLen;
                }
                if ((flags & 0x04) != 0) { // BIGRAMS
                    while (posRef[0] < buffer.capacity()) {
                        final int bgFlags = buffer.get(posRef[0]) & 0xFF;
                        final int bgAddrType = (bgFlags & 0x30);
                        if (bgAddrType == 0x10) posRef[0] += 2;
                        else if (bgAddrType == 0x20) posRef[0] += 3;
                        else if (bgAddrType == 0x30) posRef[0] += 4;
                        else posRef[0] += 1;

                        if ((bgFlags & 0x80) == 0) {
                            break;
                        }
                    }
                }
            }

            if (childrenPos > 0 && childrenPos < buffer.capacity() && !visitedOffsets.get(childrenPos)) {
                childrenToVisit.add(new int[]{childrenPos});
                childPrefixes.add(prefix.toString());
            }

            prefix.setLength(prefixLengthBefore);
        }

        final int childCount = childrenToVisit.size();
        for (int i = 0; i < childCount; i++) {
            final int chPos = childrenToVisit.get(i)[0];
            final String chPrefix = childPrefixes.get(i);
            final StringBuilder nextPrefix = new StringBuilder(chPrefix);
            parsePtNodeArray(buffer, chPos, nextPrefix, words, visitedOffsets, depth + 1);
        }
    }
}
