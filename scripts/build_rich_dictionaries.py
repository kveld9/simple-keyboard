import urllib.request
import os
import math
import sys
from compile_dict import compile_dict
from aosp_decoder import decode_aosp_dict

LANGUAGES = {
    'es': {
        'freq_url': 'https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/es/es_full.txt',
        'aosp_url': 'https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/dictionaries/main_es.dict',
        'hunspell_dic': 'https://raw.githubusercontent.com/LibreOffice/dictionaries/master/es/es_ES.dic',
    },
    'en': {
        'freq_url': 'https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_full.txt',
        'aosp_url': 'https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/dictionaries/main_en_us.dict',
        'hunspell_dic': 'https://raw.githubusercontent.com/LibreOffice/dictionaries/master/en/en_US.dic',
    },
    'ru': {
        'freq_url': 'https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/ru/ru_full.txt',
        'aosp_url': 'https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/dictionaries/main_ru.dict',
        'hunspell_dic': 'https://raw.githubusercontent.com/LibreOffice/dictionaries/master/ru_RU/ru_RU.dic',
    },
}

CACHE_DIR = 'app/build/dict_cache'
OUTPUT_DIR = 'dictionaries'

os.makedirs(CACHE_DIR, exist_ok=True)
os.makedirs(OUTPUT_DIR, exist_ok=True)


def download_cached(url, filepath):
    if os.path.exists(filepath):
        print(f'  [Cache] {filepath}')
        return
    print(f'  [Download] {url} -> {filepath}...')
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req, timeout=30) as resp:
        content = resp.read()
    with open(filepath, 'wb') as f:
        f.write(content)


def is_valid_word(word):
    if not (1 <= len(word) <= 32):
        return False
    for c in word:
        code = ord(c)
        if code > 0xFFFF or code < 0x20:
            return False
        if c.isdigit() or c in '_-+=/\\@#$%^&*()<>[]{}|~`.,!?;:"\'\t\r\n':
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


def build_unified_dictionary(lang, config):
    print(f"\n==================================================")
    print(f"  Building Unified Dictionary for [{lang.upper()}]")
    print(f"==================================================")

    # 1. Download & parse AOSP / Helium314 dictionary (Ground Truth Vocabulary)
    aosp_path = os.path.join(CACHE_DIR, f'{lang}_aosp.dict')
    download_cached(config['aosp_url'], aosp_path)

    with open(aosp_path, 'rb') as f:
        aosp_bytes = f.read()

    locale, aosp_words = decode_aosp_dict(aosp_bytes)
    print(f'  AOSP Ground-Truth Vocabulary: {len(aosp_words)} words decoded.')

    # word -> raw_frequency_score
    word_freq_map = {}
    valid_vocab_set = set()

    for w, f in aosp_words:
        if is_valid_word(w):
            valid_vocab_set.add(w)
            valid_vocab_set.add(w.lower())
            # Convert AOSP frequency (1..255) to proportional baseline count
            word_freq_map[w] = int(math.exp(f / 20.0))

    # 2. Download Hunspell dictionary for additional lexical forms
    dic_path = os.path.join(CACHE_DIR, f'{lang}.dic')
    download_cached(config['hunspell_dic'], dic_path)
    hunspell_words = parse_hunspell_dic(dic_path)
    for hw in hunspell_words:
        if is_valid_word(hw):
            valid_vocab_set.add(hw)
            if hw not in word_freq_map:
                word_freq_map[hw] = 10

    print(f'  Total validated vocabulary baseline: {len(word_freq_map)} words.')

    # 3. Download & parse FrequencyWords (HermitDave) to calibrate usage frequencies
    freq_path = os.path.join(CACHE_DIR, f'{lang}_freq.txt')
    download_cached(config['freq_url'], freq_path)

    calibrated_count = 0
    with open(freq_path, 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) >= 2:
                raw_word = parts[0]
                try:
                    count = int(parts[1])
                except ValueError:
                    continue

                if raw_word in valid_vocab_set:
                    target_word = raw_word
                elif raw_word.lower() in valid_vocab_set:
                    target_word = raw_word.lower()
                elif raw_word.capitalize() in valid_vocab_set:
                    target_word = raw_word.capitalize()
                else:
                    continue

                # Calibrate frequency with modern usage
                current_val = word_freq_map.get(target_word, 0)
                word_freq_map[target_word] = max(current_val, count)
                calibrated_count += 1

    print(f'  Frequency calibration: updated {calibrated_count} words from real-world usage.')

    # 4. Sort by frequency (full vocabulary or top_n if configured)
    limit = config.get('top_n')
    sorted_words = sorted(word_freq_map.items(), key=lambda x: x[1], reverse=True)
    if limit:
        sorted_words = sorted_words[:limit]

    max_c = sorted_words[0][1]
    min_c = sorted_words[-1][1]
    log_max = math.log(max(max_c, 2))
    log_min = math.log(max(min_c, 1))
    diff = max(log_max - log_min, 1.0)

    # 5. Quantize frequency to 1..255
    quantized_txt = os.path.join(CACHE_DIR, f'{lang}_unified_{len(sorted_words)}.txt')
    with open(quantized_txt, 'w', encoding='utf-8') as f:
        for word, count in sorted_words:
            log_c = math.log(max(count, 1))
            freq = int(round(1 + 254 * (log_c - log_min) / diff))
            freq = min(max(freq, 1), 255)
            f.write(f'{word} {freq}\n')

    # 6. Compile directly to SKDB .bin
    bin_path = os.path.join(OUTPUT_DIR, f'dict_{lang}.bin')
    print(f'  Compiling unified binary trie -> {bin_path}...')
    compile_dict(quantized_txt, bin_path)
    file_size_mb = os.path.getsize(bin_path) / (1024.0 * 1024.0)
    print(f'  [SUCCESS] Generated {bin_path} ({len(sorted_words)} words, {file_size_mb:.2f} MB)')


def main():
    target_langs = sys.argv[1:] if len(sys.argv) > 1 else list(LANGUAGES.keys())
    for lang in target_langs:
        if lang in LANGUAGES:
            build_unified_dictionary(lang, LANGUAGES[lang])
        else:
            print(f'Unknown language: {lang}')
    print('\nAll unified dictionaries successfully built in dictionaries/ !')


if __name__ == '__main__':
    main()
