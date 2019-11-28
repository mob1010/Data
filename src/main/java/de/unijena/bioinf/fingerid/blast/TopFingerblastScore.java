package de.unijena.bioinf.fingerid.blast;


import de.unijena.bioinf.ChemistryBase.algorithm.scoring.FormulaScore;

public class TopFingerblastScore extends FormulaScore {

    public TopFingerblastScore(double score) {
        super(score);
    }

    @Override
    public ScoreType getScoreType() {
        return ScoreType.Logarithmic;
    }

    @Override
    public String name() {
        return "CSI:FingerID_Score";
    }
}
