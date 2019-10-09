package de.unijena.bioinf.projectspace;

import de.unijena.bioinf.ChemistryBase.algorithm.scoring.FormulaScore;
import de.unijena.bioinf.ChemistryBase.algorithm.scoring.SScored;
import de.unijena.bioinf.ChemistryBase.chem.PrecursorIonType;
import de.unijena.bioinf.ChemistryBase.ms.Ms2Experiment;
import de.unijena.bioinf.ChemistryBase.ms.ft.FTree;
import de.unijena.bioinf.ChemistryBase.utils.FileUtils;
import de.unijena.bioinf.ms.annotations.DataAnnotation;
import de.unijena.bioinf.projectspace.sirius.CompoundContainer;
import de.unijena.bioinf.projectspace.sirius.FormulaResult;
import de.unijena.bioinf.projectspace.sirius.FormulaResultRankingScore;
import de.unijena.bioinf.projectspace.sirius.SiriusLocations;
import de.unijena.bioinf.sirius.FTreeMetricsHelper;
import de.unijena.bioinf.sirius.scores.SiriusScore;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SiriusProjectSpace implements Iterable<CompoundContainerId>, AutoCloseable {


    protected final File root;
    protected final ConcurrentHashMap<String, CompoundContainerId> ids;
    protected final ProjectSpaceConfiguration configuration;
    protected final AtomicInteger compoundCounter;
    private final ConcurrentHashMap<Class<? extends ProjectSpaceProperty>, ProjectSpaceProperty> projectSpaceProperties;

    protected ConcurrentLinkedQueue<ProjectSpaceListener> projectSpaceListeners;
    protected ConcurrentLinkedQueue<ContainerListener> compoundListeners, formulaResultListener;

    protected SiriusProjectSpace(ProjectSpaceConfiguration configuration, File root) {
        this.configuration = configuration;
        this.ids = new ConcurrentHashMap<>();
        this.root = root;
        this.compoundCounter = new AtomicInteger(-1);
        this.projectSpaceListeners = new ConcurrentLinkedQueue<>();
        this.projectSpaceProperties = new ConcurrentHashMap<>();
        this.compoundListeners = new ConcurrentLinkedQueue<>();
        this.formulaResultListener = new ConcurrentLinkedQueue<>();
    }

    public Path getRootPath() {
        return root.toPath();
    }

    public void addProjectSpaceListener(ProjectSpaceListener listener) {
        projectSpaceListeners.add(listener);
    }

    public ContainerListener.PartiallyListeningFluentBuilder<CompoundContainerId, CompoundContainer> defineCompoundListener() {
        return new ContainerListener.PartiallyListeningFluentBuilder<>(compoundListeners);
    }

    public ContainerListener.PartiallyListeningFluentBuilder<FormulaResultId, FormulaResult> defineFormulaResultListener() {
        return new ContainerListener.PartiallyListeningFluentBuilder<>(formulaResultListener);
    }

    protected void fireProjectSpaceChange(ProjectSpaceEvent event) {
        for (ProjectSpaceListener listener : this.projectSpaceListeners)
            listener.projectSpaceChanged(event);
    }

    protected synchronized void open() throws IOException {
        ids.clear();
        int maxIndex = 0;
        for (File dir : root.listFiles()) {
            final File expInfo = new File(dir, SiriusLocations.COMPOUND_INFO);
            if (dir.isDirectory() && expInfo.exists()) {
                final Map<String, String> keyValues = FileUtils.readKeyValues(expInfo);
                final int index = Integer.parseInt(keyValues.getOrDefault("index", "0"));
                final String name = keyValues.getOrDefault("name", "");
                final String dirName = dir.getName();
                final double ionMass = Double.parseDouble(keyValues.getOrDefault("ionMass", String.valueOf(Double.NaN)));

                PrecursorIonType ionType = null;
                if (keyValues.containsKey("ionType"))
                    try {
                        ionType = PrecursorIonType.fromString(keyValues.get("ionType"));
                    } catch (Exception e) {
                        LoggerFactory.getLogger(getClass()).warn("Could not parse ionType of '" + dirName + "'", e);
                    }

                ids.put(dirName, new CompoundContainerId(dirName, name, index, ionMass, ionType));
                maxIndex = Math.max(index, maxIndex);
            }
        }

        this.compoundCounter.set(maxIndex);
        fireProjectSpaceChange(ProjectSpaceEvent.OPENED);
    }

    public synchronized void close() throws IOException {
        this.ids.clear();
        fireProjectSpaceChange(ProjectSpaceEvent.CLOSED);
    }


    public Optional<CompoundContainerId> findCompound(String dirName) {
        return Optional.ofNullable(ids.get(dirName));
    }

    public Optional<CompoundContainer> newCompoundWithUniqueId(String compoundName, IntFunction<String> index2dirName) {
        return newUniqueCompoundId(compoundName, index2dirName)
                .map(idd -> {
                    try {
                        return getCompound(idd);
                    } catch (IOException e) {
                        return null;
                    }
                });
    }


    public Optional<CompoundContainerId> newUniqueCompoundId(String compoundName, IntFunction<String> index2dirName) {
        int index = compoundCounter.getAndIncrement();
        String dirName = index2dirName.apply(index);
        return tryCreateCompoundContainer(dirName, compoundName, index, Double.NaN);
    }

    public Optional<FormulaResultId> newUniqueFormulaResultId(@NotNull CompoundContainerId id, @NotNull FTree tree) throws IOException {
        return newFormulaResultWithUniqueId(getCompound(id), tree).map(FormulaResult::getId);
    }

    public Optional<FormulaResult> newFormulaResultWithUniqueId(@NotNull final CompoundContainer container, @NotNull final FTree tree) {
        if (!containsCompound(container.getId()))
            throw new IllegalArgumentException("Compound is not part of the project Space! ID: " + container.getId());

        final FormulaResultId fid = new FormulaResultId(container.getId(), tree.getRoot().getFormula(), tree.getAnnotationOrThrow(PrecursorIonType.class));
        fireContainerListeners(formulaResultListener, new ContainerEvent<>(ContainerEvent.EventType.CREATED, container.getId(), container, Collections.emptySet()));
        if (container.contains(fid))
            return Optional.empty(); //todo how to handle this?

        final FormulaResult r = new FormulaResult(fid);
        r.setAnnotation(FTree.class, tree);
        r.setAnnotation(FormulaScoring.class, new FormulaScoring(FTreeMetricsHelper.getScoresFromTree(tree)));
        try {
            updateFormulaResult(r, FTree.class, FormulaScoring.class);
        } catch (IOException e) {
            LoggerFactory.getLogger(getClass()).error("Could not create FormulaResult from FTree!", e);
            return Optional.empty();
        }

        //modify input container
        container.getResults().add(r.getId());
        return Optional.of(r);
    }

    private void fireContainerListeners(ConcurrentLinkedQueue<ContainerListener> formulaResultListener, ContainerEvent<CompoundContainerId, CompoundContainer> event) {
        formulaResultListener.forEach(x -> x.containerChanged(event));
    }

    protected Optional<CompoundContainerId> tryCreateCompoundContainer(String directoryName, String compoundName, int compoundIndex, double ionMass) {
        if (containsCompound(directoryName)) return Optional.empty();
        synchronized (ids) {
            if (new File(root, directoryName).exists())
                return Optional.empty();
            CompoundContainerId id = new CompoundContainerId(directoryName, compoundName, compoundIndex);
            if (ids.put(directoryName, id) != null)
                return Optional.empty();
            try {
                Files.createDirectory(new File(root, directoryName).toPath());
                try (final BufferedWriter bw = FileUtils.getWriter(new File(new File(root, id.getDirectoryName()), SiriusLocations.COMPOUND_INFO))) {
                    for (Map.Entry<String, String> kv : id.asKeyValuePairs().entrySet()) {
                        bw.write(kv.getKey() + "\t" + kv.getValue());
                        bw.newLine();
                    }
                }
                fireProjectSpaceChange(ProjectSpaceEvent.INDEX_UPDATED);
                return Optional.of(id);
            } catch (IOException e) {
                LoggerFactory.getLogger(getClass()).error("cannot create directory " + directoryName, e);
                ids.remove(id.getDirectoryName());
                return Optional.empty();
            }
        }
    }

    // shorthand methods
    public <T extends FormulaScore> List<SScored<FormulaResult, T>> getFormulaResultsOrderedBy(CompoundContainerId cid, Class<T> score, Class<? extends DataAnnotation>... components) throws IOException {
        return getFormulaResultsOrderedBy(getCompound(cid).getResults(), score, components);
    }

    public <T extends FormulaScore> List<SScored<FormulaResult, T>> getFormulaResultsOrderedBy(Collection<FormulaResultId> results, Class<T> score, Class<? extends DataAnnotation>... components) throws IOException {
        ArrayList<Class<? extends DataAnnotation>> comps = new ArrayList<>(components.length + 1);
        comps.addAll(Arrays.asList(components));
        if (!comps.contains(FormulaScoring.class))
            comps.add(FormulaScoring.class);

        //not stream because IOExceptions
        List<FormulaResult> res = new ArrayList<>(results.size());
        for (FormulaResultId fid : results)
            res.add(getFormulaResult(fid, comps.toArray(Class[]::new)));

        return res.stream().map(fr -> {
            try {
                T fs = fr.getAnnotationOrThrow(FormulaScoring.class).getAnnotationOrThrow(score);
                return new SScored<>(fr, fs);
            } catch (Exception e) {
                LoggerFactory.getLogger(getClass()).warn("Error when loading Scores of '" + fr.getId() + "' from Project Space! Score might be NaN");
                try {
                    return new SScored<>(fr, score.getConstructor(double.class).newInstance(Double.NaN));
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
                    throw new RuntimeException("Error when Instantiating Score class: " + score.getName(), e);
                }
            }
        }).sorted(Collections.reverseOrder()).collect(Collectors.toList());
    }

    public FormulaResult getFormulaResult(FormulaResultId id, Class<? extends DataAnnotation>... components) throws IOException {
        CompoundContainerId parentId = id.getParentId();
        parentId.containerLock.lock();
        try {
            return getContainer(FormulaResult.class, id, components);
        } finally {
            parentId.containerLock.unlock();
            ;
        }
    }

    public void updateFormulaResult(FormulaResult result, Class<? extends DataAnnotation>... components) throws IOException {
        CompoundContainerId parentId = result.getId().getParentId();
        parentId.containerLock.lock();
        try {
            updateContainer(FormulaResult.class, result, components);
            fireContainerListeners(formulaResultListener, new ContainerEvent(ContainerEvent.EventType.UPDATED, result.getId(), result, new HashSet<>(Arrays.asList(components))));
        } finally {
            parentId.containerLock.unlock();
        }
    }

    public void deleteFormulaResult(FormulaResultId resultId) throws IOException {
        CompoundContainerId parentId = resultId.getParentId();
        parentId.containerLock.lock();
        try {
            fireContainerListeners(formulaResultListener, new ContainerEvent(ContainerEvent.EventType.DELETED, resultId, null, Collections.emptySet()));
            deleteContainer(FormulaResult.class, resultId);
        } finally {
            parentId.containerLock.unlock();
        }
    }


    public CompoundContainer getCompound(CompoundContainerId id, Class<? extends DataAnnotation>... components) throws IOException {
        id.containerLock.lock();
        try {
            return getContainer(CompoundContainer.class, id, components);
        } finally {
            id.containerLock.unlock();
        }
    }

    public void updateCompound(CompoundContainer result, Class<? extends DataAnnotation>... components) throws IOException {
        final CompoundContainerId id = result.getId();
        id.containerLock.lock();
        try {
            updateContainer(CompoundContainer.class, result, components);
            fireContainerListeners(compoundListeners, new ContainerEvent<>(ContainerEvent.EventType.UPDATED, result.getId(), result, new HashSet<>(Arrays.asList(components))));
        } finally {
            id.containerLock.unlock();
        }
    }

    public void deleteCompound(CompoundContainerId resultId) throws IOException {
        resultId.containerLock.lock();
        try {
            fireContainerListeners(compoundListeners, new ContainerEvent<>(ContainerEvent.EventType.DELETED, resultId, null, Collections.emptySet()));
            deleteContainer(CompoundContainer.class, resultId);
            fireProjectSpaceChange(ProjectSpaceEvent.INDEX_UPDATED);
        } finally {
            resultId.containerLock.unlock();
        }
    }

    public boolean renameCompound(CompoundContainerId oldId, String newDirName) throws IOException {
        oldId.containerLock.lock();
        try {
            synchronized (ids) {
                if (ids.containsKey(newDirName))
                    return false;
                File file = new File(root, newDirName);
                if (file.exists()) {
                    return false;
                }
                try {
                    Files.move(new File(root, oldId.getDirectoryName()).toPath(), file.toPath());
                } catch (IOException e) {
                    LoggerFactory.getLogger(SiriusProjectSpace.class).error("cannot move directory", e);
                    return false;
                }
                ids.remove(oldId.getDirectoryName());
                oldId.rename(newDirName);
                ids.put(oldId.getDirectoryName(), oldId);
            }
        } finally {
            oldId.containerLock.unlock();
        }
        return true;
    }


    // generic methods


    <Id extends ProjectSpaceContainerId, Container extends ProjectSpaceContainer<Id>>
    Container getContainer(Class<Container> klass, Id id, Class<? extends DataAnnotation>... components) throws IOException {
        // read container
        final Container container = configuration.getContainerSerializer(klass).readFromProjectSpace(new FileBasedProjectSpaceReader(root, this::getProjectSpaceProperty), (r, c, f) -> {
            // read components
            for (Class k : components) {
                f.apply((Class<DataAnnotation>) k, (DataAnnotation) configuration.getComponentSerializer(klass, k).read(r, id, c));
            }
        }, id);
        return container;
    }

    <Id extends ProjectSpaceContainerId, Container extends ProjectSpaceContainer<Id>>
    void updateContainer(Class<Container> klass, Container container, Class<? extends DataAnnotation>... components) throws IOException {
        // write container
        configuration.getContainerSerializer(klass).writeToProjectSpace(new FileBasedProjectSpaceWriter(root, this::getProjectSpaceProperty), (r, c, f) -> {
            // write components
            for (Class k : components) {
                configuration.getComponentSerializer(klass, k)
                        .write(r, container.getId(), container, f.apply(k));
            }
        }, container.getId(), container);
    }

    <Id extends ProjectSpaceContainerId, Container extends ProjectSpaceContainer<Id>>
    void deleteContainer(Class<Container> klass, Id containerId) throws IOException {
        configuration.getContainerSerializer(klass).deleteFromProjectSpace(new FileBasedProjectSpaceWriter(root, this::getProjectSpaceProperty), (r, id) -> {
            // write components
            for (Class k : configuration.getAllComponentsForContainer(klass)) {
                configuration.getComponentSerializer(klass, k).delete(r, id);
            }
        }, containerId);
    }

    @NotNull
    @Override
    public Iterator<CompoundContainerId> iterator() {
        return ids.values().iterator();
    }

    public Iterator<CompoundContainerId> filteredIterator(Predicate<CompoundContainerId> predicate) {
        return ids.values().stream().filter(predicate).iterator();
    }

    public int size() {
        return compoundCounter.get();
    }

    public <T extends ProjectSpaceProperty> Optional<T> getProjectSpaceProperty(Class<T> key) {
        T property = (T) projectSpaceProperties.get(key);
        if (property == null) {
            synchronized (projectSpaceProperties) {
                property = (T) projectSpaceProperties.get(key);
                if (property != null) return Optional.of(property);
                try {
                    T read = configuration.getProjectSpacePropertySerializer(key).read(new FileBasedProjectSpaceReader(root, this::getProjectSpaceProperty), null, null);
                    if (read == null)
                        return Optional.empty();

                    projectSpaceProperties.put(key, read);
                    return Optional.of(read);
                } catch (IOException e) {
                    LoggerFactory.getLogger(SiriusProjectSpace.class).error(e.getMessage(), e);
                    return Optional.empty();
                }

            }
        } else return Optional.of(property);
    }

    public <T extends ProjectSpaceProperty> T setProjectSpaceProperty(Class<T> key, T value) {
        synchronized (projectSpaceProperties) {
            try {
                configuration.getProjectSpacePropertySerializer(key).write(new FileBasedProjectSpaceWriter(root, this::getProjectSpaceProperty), null, null, value != null ? Optional.of(value) : Optional.empty());
            } catch (IOException e) {
                LoggerFactory.getLogger(SiriusProjectSpace.class).error(e.getMessage(), e);
            }
            return (T) projectSpaceProperties.put(key, value);
        }
    }

    public boolean containsCompound(String dirName) {
        return findCompound(dirName).isPresent();
    }

    public boolean containsCompound(CompoundContainerId id) {
        return containsCompound(id.getDirectoryName());
    }

    public void updateSummaries(Summarizer... summarizers) throws IOException {
        Class[] annotations = Arrays.stream(summarizers).flatMap(s -> s.requiredFormulaResultAnnotations().stream()).distinct().collect(Collectors.toList()).toArray(Class[]::new);
        for (CompoundContainerId cid : ids.values()) {
            CompoundContainer c = getCompound(cid, Ms2Experiment.class);
            Ms2Experiment ex = c.getAnnotationOrThrow(Ms2Experiment.class);
            final Class<? extends FormulaScore> rankingScore = ex.getAnnotation(FormulaResultRankingScore.class).orElse(new FormulaResultRankingScore(SiriusScore.class)).value;
            List<SScored<FormulaResult, SiriusScore>> results = getFormulaResultsOrderedBy(cid, rankingScore, annotations);
            for (Summarizer sim : summarizers)
                sim.addWriteCompoundSummary(new FileBasedProjectSpaceWriter(root, this::getProjectSpaceProperty), c, results);
        }

        //write summaries to project space
        for (Summarizer summarizer : summarizers)
            summarizer.writeProjectSpaceSummary(new FileBasedProjectSpaceWriter(root, this::getProjectSpaceProperty));
    }
}