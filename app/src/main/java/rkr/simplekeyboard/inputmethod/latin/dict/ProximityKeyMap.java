package rkr.simplekeyboard.inputmethod.latin.dict;

import java.util.Arrays;
import rkr.simplekeyboard.inputmethod.latin.common.StringUtils;

/**
 * Ultra-lightweight, zero-overhead key adjacency and physical proximity map
 * with O(1) direct memory lookup.
 */
public final class ProximityKeyMap {

    private static final int NUM_KEYS = 60; // 0..26 (Latin a-z, ñ), 27..59 (Cyrillic а-я, ё)
    private static final long[] ADJACENCY_MASKS = new long[NUM_KEYS];
    private static final byte[] CHAR_TO_INDEX = new byte[65536];
    private static final float[][] DISTANCE_WEIGHTS = new float[NUM_KEYS][NUM_KEYS];

    static {
        Arrays.fill(CHAR_TO_INDEX, (byte) -1);

        for (int c = 0; c < 65536; c++) {
            CHAR_TO_INDEX[c] = (byte) computeRawIndex((char) c);
        }

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

        // Precalculate full weight matrix
        for (int u = 0; u < NUM_KEYS; u++) {
            for (int v = 0; v < NUM_KEYS; v++) {
                if (u == v) {
                    DISTANCE_WEIGHTS[u][v] = 0.0f;
                } else if ((ADJACENCY_MASKS[u] & (1L << v)) != 0) {
                    DISTANCE_WEIGHTS[u][v] = 0.5f;
                } else {
                    DISTANCE_WEIGHTS[u][v] = 1.0f;
                }
            }
        }
    }

    private ProximityKeyMap() {
    }

    private static void link(final char c, final String neighbors) {
        final int u = CHAR_TO_INDEX[c];
        if (u < 0) return;
        for (int i = 0; i < neighbors.length(); i++) {
            final int v = CHAR_TO_INDEX[neighbors.charAt(i)];
            if (v >= 0 && u != v) {
                ADJACENCY_MASKS[u] |= (1L << v);
                ADJACENCY_MASKS[v] |= (1L << u);
            }
        }
    }

    private static int computeRawIndex(final char c) {
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

    public static boolean areAdjacent(final char a, final char b) {
        final int idxA = CHAR_TO_INDEX[a];
        final int idxB = CHAR_TO_INDEX[b];
        if (idxA < 0 || idxB < 0 || idxA == idxB) {
            return false;
        }
        return (ADJACENCY_MASKS[idxA] & (1L << idxB)) != 0;
    }

    public static float getDistanceWeight(final char a, final char b) {
        if (a == b) return 0.0f;
        final int idxA = CHAR_TO_INDEX[a];
        final int idxB = CHAR_TO_INDEX[b];
        if (idxA >= 0 && idxB >= 0) {
            return DISTANCE_WEIGHTS[idxA][idxB];
        }
        return 1.0f;
    }
}
