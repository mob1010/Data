package de.unijena.bioinf.FragmentationTreeConstruction.ftheuristics;

import de.unijena.bioinf.ChemistryBase.ms.ft.FGraph;
import de.unijena.bioinf.ChemistryBase.ms.ft.FTree;
import de.unijena.bioinf.ChemistryBase.ms.ft.Fragment;
import de.unijena.bioinf.ChemistryBase.ms.ft.Loss;

import java.util.BitSet;

public class PrimStarHeuristic extends AbstractHeuristic {

    protected Loss[] edges;
    protected int edgeSize;
    protected BitSet selectedColors;

    public PrimStarHeuristic(FGraph graph) {
        super(graph);
        edges = new Loss[graph.numberOfEdges()];
        edgeSize = 0;
        selectedColors = new BitSet(ncolors);
    }

    public FTree solve() {
        int k=0;
        for (Loss l : graph.getRoot().getOutgoingEdges())
            edges[k++] = l;
        edgeSize = k;
        compute();
        return buildSolution(true);
    }

    private void compute() {
        Loss l;
        while (edgeSize>0 && (l = findMax())!=null) {
            selectedEdges.add(l);
            final Fragment v = l.getTarget();
            selectedColors.set(v.getColor());
            filterOut(v.getColor());
            for (int i=0; i < v.getOutDegree(); ++i) {
                if (!selectedColors.get(v.getChildren(i).getColor()))
                    edges[edgeSize++] = v.getOutgoingEdge(i);
            }

        }
    }

    private void filterOut(int c) {
        int k=0;
        for (int i=0; i < edgeSize; ++i) {
            if (edges[i].getTarget().getColor()!=c) {
                edges[k++] = edges[i];
            }
        }
        edgeSize = k;
    }

    private Loss findMax() {
        double maxWeight=Double.NEGATIVE_INFINITY;
        int maxIndex=-1;
        for (int i=0; i < edgeSize; ++i) {
            final double w = edges[i].getWeight();
            if (w > maxWeight) {
                maxWeight = w;
                maxIndex = i;
            }
        }
        return maxIndex<0 ? null : edges[maxIndex];
    }


}
