import urllib.request
import os
import math
from compile_dict import compile_dict
from spylls.hunspell import Dictionary

LANGUAGES = {
    'es': ('https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/es/es_full.txt', 120000),
    'en': ('https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_full.txt', 120000),
    'ru': ('https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/ru/ru_full.txt', 120000),
}

HUNSPELL_URLS = {
    'es': ('https://raw.githubusercontent.com/LibreOffice/dictionaries/master/es/es_ES.dic',
           'https://raw.githubusercontent.com/LibreOffice/dictionaries/master/es/es_ES.aff'),
    'en': ('https://raw.githubusercontent.com/LibreOffice/dictionaries/master/en/en_US.dic',
           'https://raw.githubusercontent.com/LibreOffice/dictionaries/master/en/en_US.aff'),
    'ru': ('https://raw.githubusercontent.com/LibreOffice/dictionaries/master/ru_RU/ru_RU.dic',
           'https://raw.githubusercontent.com/LibreOffice/dictionaries/master/ru_RU/ru_RU.aff'),
}

CACHE_DIR = 'app/build/dict_cache'
OUTPUT_DIR = 'dictionaries'

os.makedirs(CACHE_DIR, exist_ok=True)
os.makedirs(OUTPUT_DIR, exist_ok=True)

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

    dic_path = os.path.join(CACHE_DIR, f'{lang}.dic')
    aff_path = os.path.join(CACHE_DIR, f'{lang}.aff')
    dic_url, aff_url = HUNSPELL_URLS[lang]
    if not os.path.exists(dic_path):
        print(f'Downloading {lang} Hunspell .dic...')
        req = urllib.request.Request(dic_url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=30) as resp:
            with open(dic_path, 'wb') as f:
                f.write(resp.read())
    if not os.path.exists(aff_path):
        print(f'Downloading {lang} Hunspell .aff...')
        req = urllib.request.Request(aff_url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=30) as resp:
            with open(aff_path, 'wb') as f:
                f.write(resp.read())

    print(f'Loading Hunspell dictionary for {lang}...')
    hunspell_dict = Dictionary.from_files(os.path.join(CACHE_DIR, lang))
    print(f'Hunspell dictionary for {lang} loaded.')

    words = []
    seen = set()
    with open(cache_file, 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) >= 2:
                raw_word = parts[0]
                try:
                    count = int(parts[1])
                except ValueError:
                    continue
                if not (1 <= len(raw_word) <= 32 and not any(c.isdigit() or c in '_-+=/\\@#$%^&*()<>[]{}|~`.,!?;:"' for c in raw_word)):
                    continue

                target_word = None
                if hunspell_dict.lookup(raw_word.lower()):
                    target_word = raw_word.lower()
                elif hunspell_dict.lookup(raw_word):
                    target_word = raw_word
                elif hunspell_dict.lookup(raw_word.capitalize()):
                    target_word = raw_word.capitalize()

                if target_word and target_word not in seen:
                    seen.add(target_word)
                    words.append((target_word, count))
                    if len(words) >= top_n:
                        break

    print(f'Filtered {len(words)} valid words for {lang}.')
    if not words:
        continue

    max_c = words[0][1]
    min_c = words[-1][1]
    log_max = math.log(max(max_c, 2))
    log_min = math.log(max(min_c, 1))
    diff = max(log_max - log_min, 1.0)

    # Log-frequency quantization (1..255)
    quantized_txt = os.path.join(CACHE_DIR, f'{lang}_{len(words)}.txt')
    with open(quantized_txt, 'w', encoding='utf-8') as f:
        for word, count in words:
            log_c = math.log(max(count, 1))
            freq = int(round(1 + 254 * (log_c - log_min) / diff))
            freq = min(max(freq, 1), 255)
            f.write(f'{word} {freq}\n')

    bin_path = os.path.join(OUTPUT_DIR, f'dict_{lang}.bin')
    print(f'Compiling {lang} binary trie with {len(words)} words -> {bin_path}...')
    compile_dict(quantized_txt, bin_path)
    print(f'Done {lang}!')

print('All clean dictionaries compiled and saved to dictionaries/!')
