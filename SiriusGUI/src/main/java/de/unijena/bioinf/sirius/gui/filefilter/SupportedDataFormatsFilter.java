package de.unijena.bioinf.sirius.gui.filefilter;

import javax.swing.filechooser.FileFilter;
import java.io.File;

public class SupportedDataFormatsFilter extends FileFilter {

    @Override
    public boolean accept(File f) {
        if (f.isDirectory()) return true;
        String name = f.getName();
        name = name.toLowerCase();
        if (name.endsWith(".ms") || name.endsWith(".csv") || name.endsWith(".txt") || name.endsWith(".mgf")) {
            return true;
        }
        return false;
    }

    @Override
    public String getDescription() {
        return ".ms, .csv, .txt, .mgf";
    }

}
