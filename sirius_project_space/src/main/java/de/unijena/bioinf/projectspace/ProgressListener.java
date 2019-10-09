package de.unijena.bioinf.projectspace;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ProgressListener {
    void doOnProgress(int currentProgress, int maxProgress, @NotNull String Message);

    default void doOnProgress(int currentProgress, int maxProgress) {
        doOnProgress(currentProgress, maxProgress, "");
    }
}