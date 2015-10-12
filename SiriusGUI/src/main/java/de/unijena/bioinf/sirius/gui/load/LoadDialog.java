package de.unijena.bioinf.sirius.gui.load;

import de.unijena.bioinf.myxo.structure.CompactSpectrum;

public interface LoadDialog {

    public void newCollisionEnergy(CompactSpectrum sp);

    public void spectraAdded(CompactSpectrum sp);

    public void spectraRemoved(CompactSpectrum sp);

    public void msLevelChanged(CompactSpectrum sp);

    public void addLoadDialogListener(LoadDialogListener ldl);

    public void showDialog();

    public void experimentNameChanged(String name);

}
