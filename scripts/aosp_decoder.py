import struct

MAGIC_AOSP_V202 = 0x9BC13AFE
MAGIC_AOSP_V2 = 0x9BCB00FE
MAGIC_AOSP_V4 = 0x9BCB00FD

MAX_PARSE_DEPTH = 64
MAX_DECODED_WORDS = 300000
MAX_WORD_LENGTH = 64


def decode_aosp_dict(file_bytes):
    """
    Decodes an AOSP .dict binary buffer (v2, v202, v4) into a list of (word, frequency).
    Returns (locale, words_list).
    """
    if len(file_bytes) < 16:
        raise ValueError("File too short for AOSP dictionary")

    magic = struct.unpack('>I', file_bytes[:4])[0]
    if magic not in (MAGIC_AOSP_V202, MAGIC_AOSP_V2, MAGIC_AOSP_V4):
        raise ValueError(f"Unsupported AOSP dictionary magic: {hex(magic)}")

    version = struct.unpack('>H', file_bytes[4:6])[0]
    header_size = struct.unpack('>I', file_bytes[8:12])[0]

    if header_size < 12 or header_size > len(file_bytes):
        raise ValueError(f"Invalid header size: {header_size}")

    # Parse header attributes
    attributes = {}
    pos = 12
    key_values = []
    current_str = []

    while pos < header_size and pos < len(file_bytes):
        b = file_bytes[pos]
        if b == 0x1F:
            key_values.append(''.join(current_str))
            current_str = []
            pos += 1
        elif b >= 0x20:
            current_str.append(chr(b))
            pos += 1
        else:
            if pos + 2 < header_size:
                b1 = file_bytes[pos + 1]
                b2 = file_bytes[pos + 2]
                cp = (b << 16) | (b1 << 8) | b2
                if 0 <= cp <= 0x10FFFF:
                    current_str.append(chr(cp))
                    pos += 3
                else:
                    pos += 1
            else:
                pos += 1

    for i in range(0, len(key_values) - 1, 2):
        attributes[key_values[i]] = key_values[i + 1]

    locale = attributes.get('locale', '')
    if not locale and 'dictionary' in attributes:
        dict_id = attributes['dictionary']
        if ':' in dict_id:
            locale = dict_id.split(':', 1)[1]

    words = []
    visited_offsets = set()

    def read_char(pos):
        if pos >= len(file_bytes):
            return -1, pos
        b0 = file_bytes[pos]
        pos += 1
        if b0 == 0x1F:
            return -1, pos
        if b0 >= 0x20:
            return b0, pos
        if pos + 1 < len(file_bytes):
            b1 = file_bytes[pos]
            b2 = file_bytes[pos + 1]
            pos += 2
            cp = (b0 << 16) | (b1 << 8) | b2
            if 0 <= cp <= 0x10FFFF:
                return cp, pos
            return b0, pos - 2
        return b0, pos

    def read_ptnode_count(pos):
        if pos >= len(file_bytes):
            return 0, pos
        msb = file_bytes[pos]
        pos += 1
        if msb <= 127:
            return msb, pos
        if pos < len(file_bytes):
            lsb = file_bytes[pos]
            pos += 1
            return ((msb & 0x7F) << 8) | lsb, pos
        return msb & 0x7F, pos

    def parse_ptnode_array(group_offset, prefix_chars, depth):
        if depth > MAX_PARSE_DEPTH or len(words) >= MAX_DECODED_WORDS:
            return
        if group_offset <= 0 or group_offset >= len(file_bytes):
            return
        if group_offset in visited_offsets:
            return
        visited_offsets.add(group_offset)

        count, pos = read_ptnode_count(group_offset)
        children_to_visit = []

        for _ in range(count):
            if pos >= len(file_bytes) or len(words) >= MAX_DECODED_WORDS:
                break

            flags = file_bytes[pos]
            pos += 1

            saved_len = len(prefix_chars)

            if (flags & 0x20) != 0:  # FLAG_HAS_MULTIPLE_CHARS
                while pos < len(file_bytes) and len(prefix_chars) < MAX_WORD_LENGTH:
                    c, pos = read_char(pos)
                    if c == -1:
                        break
                    if 0 <= c <= 0x10FFFF:
                        prefix_chars.append(chr(c))
            else:
                c, pos = read_char(pos)
                if c != -1 and len(prefix_chars) < MAX_WORD_LENGTH and 0 <= c <= 0x10FFFF:
                    prefix_chars.append(chr(c))

            if (flags & 0x10) != 0:  # FLAG_IS_TERMINAL
                if pos < len(file_bytes):
                    freq = file_bytes[pos]
                    pos += 1
                    if (flags & 0x02) == 0 and len(prefix_chars) > 0:  # not NOT_A_WORD
                        words.append((''.join(prefix_chars), freq))

            addr_type = (flags & 0xC0)
            children_pos = -1
            if addr_type == 0x40 and pos < len(file_bytes):
                offset = file_bytes[pos]
                children_pos = pos + offset
                pos += 1
            elif addr_type == 0x80 and pos + 1 < len(file_bytes):
                offset = (file_bytes[pos] << 8) | file_bytes[pos + 1]
                children_pos = pos + offset
                pos += 2
            elif addr_type == 0xC0 and pos + 2 < len(file_bytes):
                offset = (file_bytes[pos] << 16) | (file_bytes[pos + 1] << 8) | file_bytes[pos + 2]
                children_pos = pos + offset
                pos += 3

            if (flags & 0x10) != 0:
                if (flags & 0x08) != 0 and pos + 1 < len(file_bytes):  # SHORTCUTS
                    sc_len = (file_bytes[pos] << 8) | file_bytes[pos + 1]
                    pos += 2 + sc_len
                if (flags & 0x04) != 0:  # BIGRAMS
                    while pos < len(file_bytes):
                        bg_flags = file_bytes[pos]
                        bg_addr_type = (bg_flags & 0x30)
                        if bg_addr_type == 0x10:
                            pos += 2
                        elif bg_addr_type == 0x20:
                            pos += 3
                        elif bg_addr_type == 0x30:
                            pos += 4
                        else:
                            pos += 1
                        if (bg_flags & 0x80) == 0:
                            break

            if 0 < children_pos < len(file_bytes) and children_pos not in visited_offsets:
                children_to_visit.append((children_pos, list(prefix_chars)))

            prefix_chars = prefix_chars[:saved_len]

        for ch_pos, ch_prefix in children_to_visit:
            parse_ptnode_array(ch_pos, ch_prefix, depth + 1)

    parse_ptnode_array(header_size, [], 0)
    return locale, words
