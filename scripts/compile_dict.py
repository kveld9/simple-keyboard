import sys
import struct
from collections import deque

def compile_dict(txt_path, bin_path):
    words = []
    with open(txt_path, 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) >= 2:
                word = parts[0]
                try:
                    freq = int(parts[1])
                except ValueError:
                    freq = 1
                words.append((word, min(max(freq, 1), 255)))

    class TrieNode:
        __slots__ = ('char', 'freq', 'is_terminal', 'children', 'word', 'offset', 'children_nodes')
        def __init__(self, char):
            self.char = char
            self.freq = 0
            self.is_terminal = False
            self.children = {}
            self.word = None
            self.offset = 0
            self.children_nodes = []

    root = TrieNode('')
    for word, freq in words:
        node = root
        for char in word:
            if char not in node.children:
                node.children[char] = TrieNode(char)
            node = node.children[char]
        node.is_terminal = True
        node.freq = freq
        node.word = word

    # String pool with linear list accumulation (O(N) instead of O(N^2))
    string_pool_parts = []
    string_pool_len = 0
    word_to_offset = {}

    def get_string_offset(w):
        nonlocal string_pool_len
        if w in word_to_offset:
            return word_to_offset[w]
        encoded = w.encode('utf-8') + b'\x00'
        offset = string_pool_len
        word_to_offset[w] = offset
        string_pool_parts.append(encoded)
        string_pool_len += len(encoded)
        return offset

    HEADER_SIZE = 16
    NODE_SIZE = 16
    current_node_offset = HEADER_SIZE

    def layout_children(nodes):
        nonlocal current_node_offset
        offset = current_node_offset
        current_node_offset += len(nodes) * NODE_SIZE
        for n in nodes:
            n.offset = offset
            offset += NODE_SIZE
        return nodes[0].offset if nodes else 0

    all_nodes = []
    queue = deque([[root]])
    while queue:
        nodes = queue.popleft()
        layout_children(nodes)
        for n in nodes:
            all_nodes.append(n)
            if n.is_terminal:
                get_string_offset(n.word)
            if n.children:
                children_list = [n.children[c] for c in sorted(n.children.keys())]
                n.children_nodes = children_list
                queue.append(children_list)

    string_pool_start = current_node_offset
    string_pool_bytes = b''.join(string_pool_parts)

    all_nodes.sort(key=lambda x: x.offset)

    # Pack in large chunk buffer for instant write
    node_bytes = bytearray(len(all_nodes) * NODE_SIZE)
    struct_pack_into = struct.pack_into

    for i, n in enumerate(all_nodes):
        char_val = ord(n.char) if n.char else 0
        flags = 0
        if n.is_terminal:
            flags |= 1
        if n.children_nodes:
            flags |= 2

        freq = n.freq
        child_count = min(len(n.children_nodes), 255)
        children_offset = n.children_nodes[0].offset if n.children_nodes else 0
        word_offset = (string_pool_start + word_to_offset[n.word]) if n.is_terminal else 0

        struct_pack_into('<HBBB3xII', node_bytes, i * NODE_SIZE, char_val, flags, freq, child_count, children_offset, word_offset)

    with open(bin_path, 'wb') as f:
        # Magic (4) + Version (4) + Word count (4) + Root offset (4)
        f.write(struct.pack('<IIII', 0x42444B53, 1, len(words), root.offset))
        f.write(node_bytes)
        f.write(string_pool_bytes)

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print("Usage: compile_dict.py <in.txt> <out.bin>")
        sys.exit(1)
    compile_dict(sys.argv[1], sys.argv[2])
