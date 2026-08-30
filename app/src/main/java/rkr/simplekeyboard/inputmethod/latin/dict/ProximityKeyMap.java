package rkr.simplekeyboard.inputmethod.latin.dict;

import rkr.simplekeyboard.inputmethod.latin.common.StringUtils;

/**
 * Ultra-lightweight and efficient key adjacency and physical proximity map
 * for QWERTY and Spanish (QWERTY with 'ñ') layouts.
 */
public final class ProximityKeyMap {

    private static final int NUM_KEYS = 27; // 'a'-'z' (0..25) and 'ñ' (26)
    private static final int[] ADJACENCY_MASKS = new int[NUM_KEYS];

    static {
        // Row 1: q w e r t y u i o p
        // Row 2: a s d f g h j k l ñ
        // Row 3: z x c v b n m
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
    }

    private ProximityKeyMap() {
    }

    private static void link(final char c, final String neighbors) {
        final int u = getIndex(c);
        if (u < 0) return;
        for (int i = 0; i < neighbors.length(); i++) {
            final int v = getIndex(neighbors.charAt(i));
            if (v >= 0 && u != v) {
                ADJACENCY_MASKS[u] |= (1 << v);
                ADJACENCY_MASKS[v] |= (1 << u);
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
        return (ADJACENCY_MASKS[idxA] & (1 << idxB)) != 0;
    }

    /**
     * Returns the physical distance weight between two characters:
     * - 0.0f if identical
     * - 0.5f if physically adjacent
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
