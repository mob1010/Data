package de.unijena.bioinf.fingerid;

import de.unijena.bioinf.ChemistryBase.fp.CdkFingerprintVersion;
import de.unijena.bioinf.ChemistryBase.fp.MaskedFingerprintVersion;
import de.unijena.bioinf.ChemistryBase.fp.PredictionPerformance;
import de.unijena.bioinf.ChemistryBase.ms.Ms2Experiment;
import de.unijena.bioinf.WebAPI;
import de.unijena.bioinf.chemdb.FingerblastSearchEngine;
import de.unijena.bioinf.chemdb.SearchableDatabase;
import de.unijena.bioinf.chemdb.SearchableDatabases;
import de.unijena.bioinf.confidence_score.CSICovarianceConfidenceScorer;
import de.unijena.bioinf.confidence_score.svm.TrainedSVM;
import de.unijena.bioinf.fingerid.blast.CovarianceScoringMethod;
import de.unijena.bioinf.fingerid.blast.Fingerblast;
import de.unijena.bioinf.fingerid.blast.ScoringMethodFactory;
import de.unijena.bioinf.fingerid.predictor_types.PredictorType;
import de.unijena.bioinf.fingerid.predictor_types.UserDefineablePredictorType;
import de.unijena.bioinf.sirius.IdentificationResult;
import gnu.trove.list.array.TIntArrayList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * This is the API class for CSI:FingerID and Fingerblast.
 * We init a separate pedictor object for positive and negative ionization
 */
public class CSIPredictor extends AbstractStructurePredictor {
    protected MaskedFingerprintVersion fpVersion;
    protected PredictionPerformance[] performances;
    protected volatile boolean initialized;

    public CSIPredictor(PredictorType predictorType, WebAPI api) {
        super(predictorType, api);
        if (!UserDefineablePredictorType.CSI_FINGERID.contains(predictorType))
            throw new IllegalArgumentException("Illegal Predicortype for this object. CSI:FingerID positive and negative only.");
    }

    public MaskedFingerprintVersion getFingerprintVersion() {
        return fpVersion;
    }

    public PredictionPerformance[] getPerformances() {
        return performances;
    }


    @Override
    public void refreshCacheDir() throws IOException {
        database = SearchableDatabases.makeCachedRestDB(csiWebAPI);
        database.checkCache();
    }

    public synchronized boolean isInitialized() {
        return initialized;
    }

    public synchronized void initialize() throws IOException {
        if (initialized)
            throw new IllegalStateException("Predictor is already initialized"); //maybe just skip

        final TIntArrayList enabledProperties = new TIntArrayList(4096);
        final PredictionPerformance[] perf = csiWebAPI.getStatistics(predictorType, enabledProperties);
        final CdkFingerprintVersion version = csiWebAPI.getFingerprintVersion();
        final MaskedFingerprintVersion.Builder v = MaskedFingerprintVersion.buildMaskFor(version);
        v.disableAll();

        enabledProperties.forEach(index -> {
            if (index >= version.size())
                System.out.println("Illegal Index");
            v.enable(index);
            return true;
        });

        performances = perf;
        fpVersion = v.toMask();

        //todo @Kai, @Martin & @Marcus: Negative covariance/confidence score?
        final CovarianceScoringMethod cvs = csiWebAPI.getCovarianceScoring(predictorType, fpVersion, performances);
        if (cvs != null) {
            fingerblastScoring = cvs;
            confidenceScorer = makeConfidenceScorer();
        } else {
            //fallback if covariance scoring does not work -> no confidence without covariance score
            fingerblastScoring = new ScoringMethodFactory.CSIFingerIdScoringMethod(performances);
            confidenceScorer = null;
        }

        trainingStructures = TrainingStructuresPerPredictor.getInstance().getTrainingStructuresSet(predictorType, csiWebAPI);
        refreshCacheDir();
        initialized = true;
    }





    private CSICovarianceConfidenceScorer makeConfidenceScorer() {
        try {
            final Map<String, TrainedSVM> confidenceSVMs = csiWebAPI.getTrainedConfidence(predictorType);

            if (confidenceSVMs == null || confidenceSVMs.isEmpty())
                throw new IOException("WebAPI returned empty confidence SVMs");

            final CovarianceScoringMethod covarianceScoring = ((CovarianceScoringMethod) fingerblastScoring);
            final ScoringMethodFactory.CSIFingerIdScoringMethod csiScoring = new ScoringMethodFactory.CSIFingerIdScoringMethod(performances);

            return new CSICovarianceConfidenceScorer(confidenceSVMs, covarianceScoring, csiScoring, fingerblastScoring.getClass());
        } catch (IOException e) {
            LoggerFactory.getLogger(getClass()).error("Error when loading confidence SVMs", e);
            return null;
        }
    }

    public Fingerblast newFingerblast(SearchableDatabase searchDB) {
        final FingerblastSearchEngine searchEngine = database.getSearchEngine(searchDB);
        return new Fingerblast(fingerblastScoring, searchEngine);
    }

    public FingerIDJJob makeFingerIDJJob(@Nullable Ms2Experiment experiment, @Nullable List<IdentificationResult> formulaIDResults) {
        return new FingerIDJJob(this, experiment, formulaIDResults);
    }

    public FingerIDJJob makeFingerIDJJob() {
        return new FingerIDJJob(this);
    }
}
