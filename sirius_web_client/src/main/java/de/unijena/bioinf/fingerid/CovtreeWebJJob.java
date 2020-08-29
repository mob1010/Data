/*
 *
 *  This file is part of the SIRIUS library for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2020 Kai Dührkop, Markus Fleischauer, Marcus Ludwig, Martin A. Hoffman and Sebastian Böcker,
 *  Chair of Bioinformatics, Friedrich-Schilller University.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 3 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with SIRIUS. If not, see <https://www.gnu.org/licenses/lgpl-3.0.txt>
 */

package de.unijena.bioinf.fingerid;

import de.unijena.bioinf.ChemistryBase.chem.MolecularFormula;
import de.unijena.bioinf.ms.rest.model.JobUpdate;
import de.unijena.bioinf.ms.rest.model.covtree.CovtreeJobOutput;
import de.unijena.bioinf.webapi.WebJJob;
import org.jetbrains.annotations.NotNull;

public class CovtreeWebJJob extends WebJJob<CovtreeWebJJob, String, CovtreeJobOutput> {

    protected final MolecularFormula inputFormula;
    protected String covtree;

    public CovtreeWebJJob(MolecularFormula formula, JobUpdate<CovtreeJobOutput> jobUpdate, long currentTimeMillis) {
        super(jobUpdate.getGlobalId(), jobUpdate.getStateEnum(), currentTimeMillis);
        this.inputFormula = formula;
    }

    @Override
    protected String makeResult() {
        return covtree; //todo @Nils create useful output for scoring method here
    }

    @Override
    protected synchronized CovtreeWebJJob updateTyped(@NotNull JobUpdate<CovtreeJobOutput> update) {
        if (updateState(update)) {
            if (update.data != null)  //todo @Nils create the output here and save it in valuable way to create the scoring method from it.
                update.data.getCovtreeOpt().ifPresent(covtree -> this.covtree = covtree);
        }

        checkForTimeout();
        evaluateState();
        return this;
    }
}