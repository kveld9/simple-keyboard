package rkr.simplekeyboard.inputmethod.latin.dict.spatial;

public class SpatialCandidate implements Comparable<SpatialCandidate> {
    public final char codePoint;
    public final float probability;
    public final float logProb;

    public SpatialCandidate(char codePoint, float probability, float logProb) {
        this.codePoint = codePoint;
        this.probability = probability;
        this.logProb = logProb;
    }

    @Override
    public int compareTo(SpatialCandidate other) {
        // Sort descending by probability
        return Float.compare(other.probability, this.probability);
    }
}
