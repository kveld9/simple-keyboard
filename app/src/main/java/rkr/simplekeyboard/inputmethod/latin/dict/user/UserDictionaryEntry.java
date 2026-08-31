package rkr.simplekeyboard.inputmethod.latin.dict.user;
 
import rkr.simplekeyboard.inputmethod.latin.common.StringUtils;

public final class UserDictionaryEntry {
    public final long id;
    public final String word;
    public final String normalizedWord;
    public final int frequency;
    public final String shortcut;
    public final long timestamp;

    public UserDictionaryEntry(final long id, final String word, final String normalizedWord,
                               final int frequency, final String shortcut, final long timestamp) {
        this.id = id;
        this.word = word;
        this.normalizedWord = normalizedWord;
        this.frequency = frequency;
        this.shortcut = shortcut;
        this.timestamp = timestamp;
    }

    public UserDictionaryEntry(final String word, final int frequency) {
        this(-1, word, word != null ? StringUtils.toNormalizedLower(word) : "", frequency, null, System.currentTimeMillis());
    }
}
