package rkr.simplekeyboard.inputmethod.latin.dict;

import rkr.simplekeyboard.inputmethod.latin.common.StringUtils;

/**
 * Ultra-lightweight and efficient key adjacency and physical proximity map
 * for QWERTY and Spanish (QWERTY with 'ñ') layouts.
 */
public final class ProximityKeyMap {

    private static final int NUM_KEYS = 60; // 0..26 (Latin a-z, ñ), 27..59 (Cyrillic а-я, ё)
    private static final long[] ADJACENCY_MASKS = new long[NUM_KEYS];

    static {
        // Latin QWERTY
        link('q', "wa");
        link('w', "qeasd");
        link('e', "wrsdf");
        link('r', "etdfg");
        link('t', "ryfgh");
        link('y', "tughj");
        link('u', "yihjk");
        link('i', "uojkl");
        link('o', "ipklñ");
        link('p', "olñ");
        link('a', "qwsz");
        link('s', "awedxz");
        link('d', "serfxc");
        link('f', "drtgcv");
        link('g', "ftyhvb");
        link('h', "gyujbn");
        link('j', "huiknm");
        link('k', "jiolmñ");
        link('l', "kopñm");
        link('ñ', "lpok");
        link('z', "asx");
        link('x', "zsdc");
        link('c', "xdfv");
        link('v', "cfgb");
        link('b', "vghn");
        link('n', "bhjm");
        link('m', "njkl");

        // Cyrillic JCUKEN (Russian)
        // Row 1: й ц у к е н г ш щ з х ъ
        // Row 2: ф ы в а п р о л д ж э
        // Row 3: я ч с м и т ь б ю
        link('й', "цф");
        link('ц', "йуфыв");
        link('у', "цкыва");
        link('к', "уенвап");
        link('е', "кнгапр");
        link('н', "ешгро");
        link('г', "ншрол");
        link('ш', "гщолд");
        link('щ', "шзлдж");
        link('з', "щхджэ");
        link('х', "зъжэ");
        link('ъ', "хэ");
        link('ф', "йцыя");
        link('ы', "фцвуяч");
        link('в', "ыукачс");
        link('а', "вкапсм");
        link('п', "аперсми");
        link('р', "пенгомит");
        link('о', "ргшлтиь");
        link('л', "ошщдиьб");
        link('д', "лщзжьбю");
        link('ж', "дзхэбю");
        link('э', "жхъю");
        link('я', "фыч");
        link('ч', "яывс");
        link('с', "чвам");
        link('м', "сапи");
        link('и', "мпрт");
        link('т', "ироь");
        link('ь', "толб");
        link('б', "ьлдю");
        link('ю', "бджэ");
        link('ё', "12й");
    }

    private ProximityKeyMap() {
    }

    private static void link(final char c, final String neighbors) {
        final int u = getIndex(c);
        if (u < 0) return;
        for (int i = 0; i < neighbors.length(); i++) {
            final int v = getIndex(neighbors.charAt(i));
            if (v >= 0 && u != v) {
                ADJACENCY_MASKS[u] |= (1L << v);
                ADJACENCY_MASKS[v] |= (1L << u);
            }
        }
    }

    private static int getIndex(final char c) {
        final char lower = Character.toLowerCase(c);
        if (lower == 'ñ') {
            return 26;
        }
        final char unaccented = StringUtils.removeAccents(lower);
        if (unaccented >= 'a' && unaccented <= 'z') {
            return unaccented - 'a';
        }
        if (lower >= 'а' && lower <= 'я') {
            return 27 + (lower - 'а');
        }
        if (lower == 'ё') {
            return 59;
        }
        return -1;
    }

    /**
     * Checks whether two characters are physically adjacent on the keyboard layout.
     */
    public static boolean areAdjacent(final char a, final char b) {
        final int idxA = getIndex(a);
        final int idxB = getIndex(b);
        if (idxA < 0 || idxB < 0 || idxA == idxB) {
            return false;
        }
        return (ADJACENCY_MASKS[idxA] & (1L << idxB)) != 0;
    }

    /**
     * Returns the physical distance weight between two characters:
     * - 0.0f if identical
     * - 0.35f if physically adjacent on the keyboard
     * - 1.0f if not adjacent
     */
    public static float getDistanceWeight(final char a, final char b) {
        final int idxA = getIndex(a);
        final int idxB = getIndex(b);
        if (idxA >= 0 && idxA == idxB) {
            return 0.0f;
        }
        if (areAdjacent(a, b)) {
            return 0.5f;
        }
        return 1.0f;
    }
}
