import sys
import struct
import os

def compile_dict(txt_path, bin_path):
    # Parse txt file
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

    # Build Trie
    class TrieNode:
        def __init__(self, char):
            self.char = char
            self.freq = 0
            self.is_terminal = False
            self.children = {} # char -> TrieNode
            self.word = None
            self.offset = 0

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

    # String pool
    string_pool = b''
    word_to_offset = {}

    def get_string_offset(word):
        nonlocal string_pool
        if word in word_to_offset:
            return word_to_offset[word]
        encoded = word.encode('utf-8') + b'\x00'
        offset = len(string_pool)
        word_to_offset[word] = offset
        string_pool += encoded
        return offset

    # BFS/DFS to layout nodes and assign offsets
    # Node layout:
    # 0: char16 (2)
    # 2: flags (1) bit 0: isTerm, bit 1: hasChildren
    # 3: freq (1)
    # 4: childCount (1)
    # 5: pad (3)
    # 8: childrenOffset (4)
    # 12: wordOffset (4)
    # Total 16 bytes per node

    nodes_list = []
    def flatten_trie(node):
        nodes_list.append(node)
        for child in sorted(node.children.values(), key=lambda x: x.char):
            flatten_trie(child)
            
    flatten_trie(root)
    
    # Pre-calculate string offsets
    for node in nodes_list:
        if node.is_terminal:
            get_string_offset(node.word)

    # Calculate node offsets
    # Header: 4 (magic) + 4 (version) + 4 (wordcount) + 4 (root offset) = 16 bytes
    HEADER_SIZE = 16
    NODE_SIZE = 16
    
    current_node_offset = HEADER_SIZE
    
    # We must place children contiguous in memory? The prompt says "childrenOffset", which points to the first child.
    # To have `childrenOffset` point to the first child, and since `childCount` is known, children must be stored contiguously!
    # Wait, the TrieNode has children. If we store them contiguously, we need a different layout approach: BFS or layout by children arrays.
    
    # Let's rebuild node layout to ensure children are contiguous
    # We can allocate arrays of children.
    
    blocks = []
    
    def layout_children(nodes):
        nonlocal current_node_offset
        offset = current_node_offset
        current_node_offset += len(nodes) * NODE_SIZE
        for n in nodes:
            n.offset = offset
            offset += NODE_SIZE
        return nodes[0].offset if nodes else 0

    # root is a single node
    queue = [[root]]
    while queue:
        nodes = queue.pop(0)
        layout_children(nodes)
        for n in nodes:
            if n.children:
                children_list = [n.children[c] for c in sorted(n.children.keys())]
                n.children_nodes = children_list
                queue.append(children_list)
            else:
                n.children_nodes = []

    # Write binary
    with open(bin_path, 'wb') as f:
        # Magic
        f.write(b'SKDB')
        # Version
        f.write(struct.pack('<I', 1))
        # Word count
        f.write(struct.pack('<I', len(words)))
        # Root offset
        f.write(struct.pack('<I', root.offset))
        
        # Write nodes
        # We need to traverse in the exact same order we assigned offsets
        # Wait, just sort nodes_list by offset and write
        nodes_list.sort(key=lambda x: x.offset)
        for n in nodes_list:
            char_val = ord(n.char) if n.char else 0
            flags = 0
            if n.is_terminal:
                flags |= 1
            if n.children_nodes:
                flags |= 2
            
            freq = n.freq
            child_count = min(len(n.children_nodes), 255)
            
            children_offset = n.children_nodes[0].offset if n.children_nodes else 0
            word_offset = get_string_offset(n.word) if n.is_terminal else 0
            
            # format: H = uint16, B = uint8, x = pad byte, I = uint32
            # H (2) + B (1) + B (1) + B (1) + 3x (3) + I (4) + I (4) = 16 bytes
            f.write(struct.pack('<HBBB3xII', char_val, flags, freq, child_count, children_offset, word_offset))
            
        # We need to adjust string offsets!
        # string_pool starts at current_node_offset
        string_pool_start = current_node_offset
        # But wait, word_offset in nodes was just the offset WITHIN the string pool.
        # We should add string_pool_start to them!
        
    # Let's fix the word_offset to be absolute
    with open(bin_path, 'r+b') as f:
        for n in nodes_list:
            if n.is_terminal:
                absolute_word_offset = string_pool_start + word_to_offset[n.word]
                f.seek(n.offset + 12)
                f.write(struct.pack('<I', absolute_word_offset))
                
        f.seek(string_pool_start)
        f.write(string_pool)

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print("Usage: compile_dict.py <in.txt> <out.bin>")
        sys.exit(1)
    compile_dict(sys.argv[1], sys.argv[2])
