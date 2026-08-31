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

    @Test
    public void testDecodeAospHeaderAndNodes() throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        // 1. Attributes
        final ByteArrayOutputStream attrStream = new ByteArrayOutputStream();
        attrStream.write("locale".getBytes());
        attrStream.write(0x1F);
        attrStream.write("es".getBytes());
        attrStream.write(0x1F);
        attrStream.write("dictionary".getBytes());
        attrStream.write(0x1F);
        attrStream.write("main:es".getBytes());
        attrStream.write(0x1F);
        attrStream.write("version".getBytes());
        attrStream.write(0x1F);
        attrStream.write("100".getBytes());
        attrStream.write(0x1F);
        final byte[] attrBytes = attrStream.toByteArray();

        final int headerSize = 12 + attrBytes.length;

        // Write Header
        final ByteBuffer headerBuf = ByteBuffer.allocate(12);
        headerBuf.order(ByteOrder.BIG_ENDIAN);
        headerBuf.putInt(AospDictDecoder.MAGIC_AOSP_V202);
        headerBuf.putShort((short) 202);
        headerBuf.putShort((short) 0);
        headerBuf.putInt(headerSize);
        out.write(headerBuf.array());
        out.write(attrBytes);

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
    }
}
