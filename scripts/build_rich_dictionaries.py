#!/usr/bin/env python3
"""
scripts/build_rich_dictionaries.py

Unifies and compiles high-density, rich dictionaries for Simple Keyboard:
- Ground Truth: Canonical AOSP combined wordlists (Helium314) with regional variants (en_US + en_GB + en_AU, es, ru).
- Lexical Formal Depth: LibreOffice Hunspell full morphological dictionary.
- Real-World Usage: FrequencyWords (HermitDave) usage calibration.
- Regional & Colloquial Slang: Full Latin American and regional vocabulary.
- Pure Python compilation into SKDB v1 binary trie with zero JVM dependencies.
"""

import urllib.request
import os
import math
import re
import sys
from compile_dict import compile_dict

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE_DIR = os.path.join(BASE_DIR, "build", "dict_cache")
OUTPUT_DIR = os.path.join(BASE_DIR, "dictionaries")

os.makedirs(CACHE_DIR, exist_ok=True)
os.makedirs(OUTPUT_DIR, exist_ok=True)

WORD_RE = re.compile(r"^\s*word=([^,]+),f=(\d+)")

LANGUAGES = {
    'es': {
        'aosp_sources': [
            'https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/wordlists/main_es.combined',
        ],
        'hunspell_dic': 'https://raw.githubusercontent.com/LibreOffice/dictionaries/master/es/es_ES.dic',
        'freq_url': 'https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/es/es_full.txt',
        'regionals': {
            'boludo': 180, 'boluda': 175, 'pelotudo': 170, 'pelotuda': 165, 'laburo': 180, 'laburar': 175,
            'pibe': 185, 'piba': 180, 'quilombo': 175, 'chamuyo': 165, 'morfi': 160, 'guita': 175,
            'chabón': 170, 'chabona': 165, 'bondi': 175, 'bondis': 170, 'remera': 185, 'palta': 180,
            'mate': 195, 'fernet': 180, 'afano': 160, 'garron': 160, 'garrón': 165,
            'ahorita': 190, 'chavo': 175, 'chava': 175, 'chavos': 170, 'chamba': 180, 'chambear': 175,
            'chambeando': 175, 'cacahuate': 175, 'cacahuates': 170, 'plática': 180, 'platicar': 180,
            'chido': 180, 'chida': 175, 'güey': 185, 'lana': 180, 'neta': 180, 'escuincle': 165,
            'chamaco': 170, 'chamaca': 170, 'parcero': 175, 'parcera': 170, 'parce': 180, 'chévere': 185,
            'chevere': 180, 'bacano': 180, 'bacana': 175, 'chamo': 180, 'chama': 175, 'birras': 170,
            'guagua': 175, 'pana': 180
        }
    },
    'en': {
        'aosp_sources': [
            'https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/wordlists/main_en_US.combined',
            'https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/wordlists/main_en_GB.combined',
            'https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/wordlists/main_en_AU.combined',
        ],
        'hunspell_dic': 'https://raw.githubusercontent.com/LibreOffice/dictionaries/master/en/en_US.dic',
        'freq_url': 'https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_full.txt',
        'regionals': {}
    },
    'ru': {
        'aosp_sources': [
            'https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/wordlists/main_ru.combined',
        ],
        'hunspell_dic': 'https://raw.githubusercontent.com/LibreOffice/dictionaries/master/ru_RU/ru_RU.dic',
        'freq_url': 'https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/ru/ru_full.txt',
        'regionals': {}
    },
}

def download_cached(url, filepath):
    if os.path.exists(filepath) and os.path.getsize(filepath) > 0:
        print(f"  [Cache] {filepath}")
        return
    print(f"  [Download] {url} -> {filepath}...")
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=45) as resp:
        content = resp.read()
    with open(filepath, "wb") as f:
        f.write(content)

def is_valid_word(word):
    if not (1 <= len(word) <= 32):
        return False
    if word.startswith("'") or word.endswith("'") or word.startswith("-") or word.endswith("-"):
        return False
    for c in word:
        code = ord(c)
        if code > 0xFFFF or code < 0x20:
            return False
        if c.isdigit() or c in '_+=/\\@#$%^&*()<>[]{}|~`.,!?;:"\t\r\n':
            return False
    return True

def parse_hunspell_dic(dic_path):
    words = set()
    if not os.path.exists(dic_path):
        return words
    with open(dic_path, 'r', encoding='utf-8', errors='ignore') as f:
        for line in f:
            line = line.strip()
            if not line or line.isdigit():
                continue
            base_word = line.split('/')[0].strip()
            if is_valid_word(base_word):
                words.add(base_word)
                words.add(base_word.lower())
    return words

def parse_aosp_combined(combined_path):
    vocab = {}
    with open(combined_path, "r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            m = WORD_RE.match(line)
            if m:
                word = m.group(1).strip()
                try:
                    freq = int(m.group(2))
                except ValueError:
                    freq = 1
                if is_valid_word(word):
                    vocab[word] = max(vocab.get(word, 0), freq)
    return vocab

def build_unified_dictionary(lang, config):
    print(f"\n==================================================")
    print(f"  Building Unified Dictionary for [{lang.upper()}]")
    print(f"==================================================")

    word_freq_map = {}
    valid_vocab_set = set()

    # 1. Download & parse all AOSP Combined sources (US, UK, AU, etc.)
    for idx, src_url in enumerate(config['aosp_sources']):
        filename = f"{lang}_aosp_{idx}.combined"
        cached_file = os.path.join(CACHE_DIR, filename)
        download_cached(src_url, cached_file)
        src_vocab = parse_aosp_combined(cached_file)
        print(f"  Parsed AOSP Combined ({os.path.basename(src_url)}): {len(src_vocab):,} words.")
        for w, f in src_vocab.items():
            valid_vocab_set.add(w)
            valid_vocab_set.add(w.lower())
            converted_freq = int(math.exp(f / 20.0))
            word_freq_map[w] = max(word_freq_map.get(w, 0), converted_freq)

    # 2. Add Regional Idioms & Slang
    if config.get('regionals'):
        for rw, rf in config['regionals'].items():
            if is_valid_word(rw):
                valid_vocab_set.add(rw)
                valid_vocab_set.add(rw.lower())
                converted_freq = int(math.exp(rf / 20.0))
                word_freq_map[rw] = max(word_freq_map.get(rw, 0), converted_freq)
        print(f"  Injected {len(config['regionals'])} regionalisms and colloquial terms.")

    # 3. Download & parse Hunspell dictionary
    dic_path = os.path.join(CACHE_DIR, f"{lang}.dic")
    download_cached(config['hunspell_dic'], dic_path)
    hunspell_words = parse_hunspell_dic(dic_path)
    for hw in hunspell_words:
        if is_valid_word(hw):
            valid_vocab_set.add(hw)
            if hw not in word_freq_map:
                word_freq_map[hw] = 10

    print(f"  Total validated base vocabulary: {len(word_freq_map):,} words.")

    # 4. Download & calibrate with FrequencyWords (HermitDave)
    freq_path = os.path.join(CACHE_DIR, f"{lang}_freq.txt")
    download_cached(config['freq_url'], freq_path)
    calibrated_count = 0
    with open(freq_path, 'r', encoding='utf-8', errors='ignore') as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) >= 2:
                raw_word = parts[0]
                try:
                    count = int(parts[1])
                except ValueError:
                    continue

                if raw_word in valid_vocab_set:
                    target = raw_word
                elif raw_word.lower() in valid_vocab_set:
                    target = raw_word.lower()
                elif raw_word.capitalize() in valid_vocab_set:
                    target = raw_word.capitalize()
                else:
                    continue

                current_val = word_freq_map.get(target, 0)
                word_freq_map[target] = max(current_val, count)
                calibrated_count += 1

    print(f"  Frequency calibration: updated {calibrated_count:,} words with real-world usage.")

    # 5. Quantize frequencies to 1..255 (Logarithmic scaling)
    sorted_words = sorted(word_freq_map.items(), key=lambda x: x[1], reverse=True)
    max_c = sorted_words[0][1]
    min_c = sorted_words[-1][1]
    log_max = math.log(max(max_c, 2))
    log_min = math.log(max(min_c, 1))
    diff = max(log_max - log_min, 1.0)

    quantized_txt = os.path.join(CACHE_DIR, f"{lang}_unified_{len(sorted_words)}.txt")
    with open(quantized_txt, 'w', encoding='utf-8') as f:
        for word, count in sorted_words:
            log_c = math.log(max(count, 1))
            freq = int(round(1 + 254 * (log_c - log_min) / diff))
            freq = min(max(freq, 1), 255)
            f.write(f"{word} {freq}\n")

    # 6. Compile to SKDB binary trie format
    bin_path = os.path.join(OUTPUT_DIR, f"dict_{lang}.bin")
    print(f"  Compiling unified binary trie -> {bin_path}...")
    compile_dict(quantized_txt, bin_path)
    size_mb = os.path.getsize(bin_path) / (1024.0 * 1024.0)
    print(f"  [SUCCESS] Generated {bin_path} ({len(sorted_words):,} words, {size_mb:.2f} MB)")

def main():
    target_langs = sys.argv[1:] if len(sys.argv) > 1 else list(LANGUAGES.keys())
    for lang in target_langs:
        if lang in LANGUAGES:
            build_unified_dictionary(lang, LANGUAGES[lang])
        else:
            print(f"Unknown language: {lang}")
    print("\nAll unified dictionaries successfully built in dictionaries/ directory!")

if __name__ == "__main__":
    main()
