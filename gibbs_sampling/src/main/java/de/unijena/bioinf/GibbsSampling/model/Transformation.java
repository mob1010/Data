package de.unijena.bioinf.GibbsSampling.model;

import de.unijena.bioinf.ChemistryBase.chem.MolecularFormula;
import de.unijena.bioinf.GibbsSampling.model.Reaction;

public class Transformation implements Reaction {
    private final MolecularFormula group1;
    private final MolecularFormula group2;
    private final MolecularFormula netChange;
    private final int stepSize;

    public Transformation(MolecularFormula group1, MolecularFormula group2) {
        this(group1, group2, 1);
    }

    public Transformation(MolecularFormula group1, MolecularFormula group2, int stepSize) {
        if(group1 != null && group2 != null) {
            this.group1 = group1;
            this.group2 = group2;
            this.stepSize = stepSize;
            this.netChange = group1.add(group2.negate());
            if(this.netChange == null) {
                throw new RuntimeException("netChange null");
            }
        } else {
            throw new RuntimeException("group null");
        }
    }

    public MolecularFormula getRemovedGroup() {
        return this.group1.clone();
    }

    public MolecularFormula getAddedGroup() {
        return this.group2.clone();
    }

    public boolean hasReaction(MolecularFormula mf1, MolecularFormula mf2) {
        return mf1.isSubtractable(this.group1) && mf1.subtract(this.group1).add(this.group2).equals(mf2);
    }

    public MolecularFormula netChange() {
        return this.netChange.clone();
    }

    public Reaction negate() {
        return new Transformation(this.group2, this.group1, this.stepSize);
    }

    public int stepSize() {
        return this.stepSize;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer("Transformation{");
        sb.append(this.group1).append("->");
        sb.append(this.group2);
        sb.append('}');
        return sb.toString();
    }

    public boolean equals(Object o) {
        if(this == o) {
            return true;
        } else if(!(o instanceof Transformation)) {
            return false;
        } else {
            Transformation that = (Transformation)o;
            return this.group1.equals(that.group1);
        }
    }

    public int hashCode() {
        int result = this.group1.hashCode();
        result = 31 * result + this.group2.hashCode();
        return result;
    }
}
