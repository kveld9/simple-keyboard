package rkr.simplekeyboard.inputmethod.latin.dict.aosp;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import rkr.simplekeyboard.inputmethod.latin.dict.binary.BinaryTrieCompiler;

import static org.junit.Assert.*;

public class AospDictDecoderTest {

    private byte[] createAospHeader(String... attrs) throws IOException {
        final ByteArrayOutputStream attrStream = new ByteArrayOutputStream();
        for (int i = 0; i < attrs.length; i += 2) {
            attrStream.write(attrs[i].getBytes());
            attrStream.write(0x1F);
            attrStream.write(attrs[i + 1].getBytes());
            attrStream.write(0x1F);
        }
        final byte[] attrBytes = attrStream.toByteArray();
        final int headerSize = 12 + attrBytes.length;

        final ByteBuffer headerBuf = ByteBuffer.allocate(12);
        headerBuf.order(ByteOrder.BIG_ENDIAN);
        headerBuf.putInt(AospDictDecoder.MAGIC_AOSP_V202);
        headerBuf.putShort((short) 202);
        headerBuf.putShort((short) 0);
        headerBuf.putInt(headerSize);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(headerBuf.array());
        out.write(attrBytes);
        return out.toByteArray();
    }


    @Test
    public void testDecodeAospHeaderAndNodes() throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        out.write(createAospHeader("locale", "es", "dictionary", "main:es", "version", "100"));

        // 2. Root PtNodeArray at position = headerSize
        // Array count = 1 node
        out.write(1); // 1 node

        // Node: "hola" using FLAG_HAS_MULTIPLE_CHARS (0x20) | FLAG_IS_TERMINAL (0x10)
        final int flags = 0x20 | 0x10;
        out.write(flags);
        // Characters: 'h', 'o', 'l', 'a', terminator (0x1F)
        out.write('h');
        out.write('o');
        out.write('l');
        out.write('a');
        out.write(0x1F);
        // Frequency
        out.write(190);

        final byte[] dictBytes = out.toByteArray();
        final ByteBuffer buffer = ByteBuffer.wrap(dictBytes);
        buffer.order(ByteOrder.BIG_ENDIAN);

        final AospDictDecoder.DecodedDictionary decoded = AospDictDecoder.decode(buffer);

        assertNotNull(decoded);
        assertEquals("es", decoded.locale);
        assertEquals("es", decoded.languageCode);
        assertEquals(202, decoded.version);
        assertEquals("main:es", decoded.attributes.get("dictionary"));

        final List<BinaryTrieCompiler.WordEntry> words = decoded.words;
        assertEquals(1, words.size());
        assertEquals("hola", words.get(0).word);
        assertEquals(190, words.get(0).frequency);
        assertTrue(decoded.bigrams.isEmpty());
    }

    @Test
    public void testDecodeAospWithBigrams() throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        out.write(createAospHeader("locale", "es"));

        // 2. Root PtNodeArray: count = 2 nodes
        out.write(2);

        // Node 1: "hola" (FLAG_HAS_MULTIPLE_CHARS | FLAG_IS_TERMINAL | FLAG_HAS_BIGRAMS)
        final int node1Flags = 0x20 | 0x10 | 0x04;
        out.write(node1Flags);
        out.write('h');
        out.write('o');
        out.write('l');
        out.write('a');
        out.write(0x1F);
        out.write(200); // frequency

        // Bigram pointing to Node 2:
        // bgFlags: bit 7 = 0 (last bigram), addrType = 0x10 (1-byte addr), prob = 10 (freq 170)
        final int bgFlags = 0x10 | 10;
        out.write(bgFlags);
        // Target offset is 0 because right after this 1-byte address, Node 2 begins!
        out.write(0);

        // Node 2: "amigo" (FLAG_HAS_MULTIPLE_CHARS | FLAG_IS_TERMINAL)
        final int node2Flags = 0x20 | 0x10;
        out.write(node2Flags);
        out.write('a');
        out.write('m');
        out.write('i');
        out.write('g');
        out.write('o');
        out.write(0x1F);
        out.write(180); // frequency

        final byte[] dictBytes = out.toByteArray();
        final ByteBuffer buffer = ByteBuffer.wrap(dictBytes);
        buffer.order(ByteOrder.BIG_ENDIAN);

        final AospDictDecoder.DecodedDictionary decoded = AospDictDecoder.decode(buffer);

        assertNotNull(decoded);
        assertEquals(2, decoded.words.size());
        assertEquals("hola", decoded.words.get(0).word);
        assertEquals("amigo", decoded.words.get(1).word);

        assertEquals(1, decoded.bigrams.size());
        final BinaryTrieCompiler.BigramEntry bg = decoded.bigrams.get(0);
        assertEquals("hola", bg.word1);
        assertEquals("amigo", bg.word2);
        assertEquals(170, bg.frequency);
    }
}
