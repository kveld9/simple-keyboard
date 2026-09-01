package rkr.simplekeyboard.inputmethod.latin.dict.user;

public final class UserBigramEntry {
    public final long id;
    public final String prevWord;
    public final String word;
    public final int frequency;
    public final long timestamp;

    public UserBigramEntry(final long id, final String prevWord, final String word,
                           final int frequency, final long timestamp) {
        this.id = id;
        this.prevWord = prevWord;
        this.word = word;
        this.frequency = frequency;
        this.timestamp = timestamp;
    }

    public UserBigramEntry(final String prevWord, final String word, final int frequency) {
        this(-1, prevWord, word, frequency, System.currentTimeMillis());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final UserBigramEntry that = (UserBigramEntry) o;
        if (frequency != that.frequency) return false;
        if (prevWord != null ? !prevWord.equals(that.prevWord) : that.prevWord != null) return false;
        return word != null ? word.equals(that.word) : that.word == null;
    }

    @Override
    public int hashCode() {
        int result = prevWord != null ? prevWord.hashCode() : 0;
        result = 31 * result + (word != null ? word.hashCode() : 0);
        result = 31 * result + frequency;
        return result;
    }

    @Override
    public String toString() {
        return "UserBigramEntry{" +
                "id=" + id +
                ", prevWord='" + prevWord + '\'' +
                ", word='" + word + '\'' +
                ", frequency=" + frequency +
                ", timestamp=" + timestamp +
                '}';
    }
}
