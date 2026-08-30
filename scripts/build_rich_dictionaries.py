import urllib.request
import os
import math
from compile_dict import compile_dict

LANGUAGES = {
    'es': ('https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/es/es_full.txt', 160000),
    'en': ('https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_full.txt', 160000),
    'ru': ('https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/ru/ru_full.txt', 160000),
}

CACHE_DIR = 'app/build/dict_cache'
ASSETS_DIR = 'app/src/main/assets'

os.makedirs(CACHE_DIR, exist_ok=True)
os.makedirs(ASSETS_DIR, exist_ok=True)

for lang, (url, top_n) in LANGUAGES.items():
    cache_file = os.path.join(CACHE_DIR, f'{lang}_full.txt')
    if not os.path.exists(cache_file):
        print(f'Downloading {lang} full wordlist from {url}...')
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=30) as resp:
            content = resp.read().decode('utf-8')
        with open(cache_file, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f'Downloaded {lang} full wordlist.')
    else:
        print(f'Using cached {cache_file}...')

    # Parse and filter
    words = []
    with open(cache_file, 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) >= 2:
                word = parts[0]
                try:
                    count = int(parts[1])
                except ValueError:
                    continue
                if 1 <= len(word) <= 32 and not any(c.isdigit() or c in '_-+=/\\@#$%^&*()<>[]{}|~`.,!?;:"' for c in word):
                    words.append((word, count))

    # Take top top_n
    words = words[:top_n]
    if not words:
        continue

    max_c = words[0][1]
    min_c = words[-1][1]
    log_max = math.log(max(max_c, 2))
    log_min = math.log(max(min_c, 1))
    diff = max(log_max - log_min, 1.0)

    # Log-frequency quantization (1..255)
    quantized_txt = os.path.join(CACHE_DIR, f'{lang}_{top_n}.txt')
    with open(quantized_txt, 'w', encoding='utf-8') as f:
        for word, count in words:
            log_c = math.log(max(count, 1))
            freq = int(round(1 + 254 * (log_c - log_min) / diff))
            freq = min(max(freq, 1), 255)
            f.write(f'{word} {freq}\n')

    bin_path = os.path.join(ASSETS_DIR, f'dict_{lang}.bin')
    print(f'Compiling {lang} binary trie with {len(words)} words -> {bin_path}...')
    compile_dict(quantized_txt, bin_path)
    print(f'Done {lang}!')

print('All 160k dictionaries compiled and saved to assets!')
