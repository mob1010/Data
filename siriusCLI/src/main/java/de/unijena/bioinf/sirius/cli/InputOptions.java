package de.unijena.bioinf.sirius.cli;

import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;
import de.unijena.bioinf.ChemistryBase.chem.FormulaConstraints;

import java.io.File;
import java.util.List;

public interface InputOptions {

    @Option(shortName = "1", longName = "ms1", description = "MS1 spectrum file name", minimum = 0, defaultToNull = true)
    public List<File> getMs1();

    @Option(shortName = "2", longName = "ms2", description = "MS2 spectra file names", minimum = 0, defaultToNull = true)
    public List<File> getMs2();

    @Option(shortName = "z", longName = {"parentmass", "precursor", "mz"}, description = "the mass of the parent ion", defaultToNull = true)
    public Double getParentMz();

    @Option(shortName = "i", longName = "ion", description = "the ionization/adduct of the MS/MS data. Example: [M+H]+, [M-H]-, [M+Cl]-, [M+Na]+, [M]+.", defaultToNull =true)
    public String getIon();

    @Unparsed
    public List<String> getInput();


    @Option(shortName = "e", longName = "elements", description = "The allowed elements. Write CHNOPSCl to allow the elements C, H, N, O, P, S and Cl. Add numbers in brackets to restrict the maximal allowed occurence of these elements: CHNOP[5]S[8]Cl[1]", defaultToNull = true)
    public FormulaConstraints getElements();

}
