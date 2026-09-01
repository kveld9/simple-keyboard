package rkr.simplekeyboard.inputmethod.latin.dict.spatial;

public class SpatialCandidate implements Comparable<SpatialCandidate> {
    public char codePoint;
    public float probability;
    public float logProb;

    public SpatialCandidate() {
        this('\0', 0.0f, 0.0f);
    }

    public SpatialCandidate(char codePoint, float probability, float logProb) {
        this.codePoint = codePoint;
        this.probability = probability;
        this.logProb = logProb;
    }

    public void set(char codePoint, float probability, float logProb) {
        this.codePoint = codePoint;
        this.probability = probability;
        this.logProb = logProb;
    }

    public static SpatialCandidate exact(final int codePoint) {
        return new SpatialCandidate((char) codePoint, 1.0f, 0.0f);
    }

    @Override
    public int compareTo(SpatialCandidate other) {
        // Sort descending by probability
        return Float.compare(other.probability, this.probability);
    }
}

