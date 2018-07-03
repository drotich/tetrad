package edu.pitt.csb;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SearchLogUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;
import edu.pitt.csb.mgm.EigenDecomposition;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.random.SynchronizedRandomGenerator;
import org.apache.commons.math3.random.Well44497b;

import java.util.*;

import static com.google.common.primitives.Doubles.asList;
import static edu.cmu.tetrad.util.StatUtils.median;
import static java.lang.Math.*;

/**
 * Kernel Based Conditional Independence Test
 * Code Written by: Vineet Raghu
 * Test published in: Kernel-based Conditional Independence Test and Application in Causal Discovery (Zhang et al.)
 *
 * @author vinee_000 on 7/3/2016
 * @author jdramsey refactoring 6/17/2018
 */
public class KCI implements IndependenceTest {
    private final int N;
    private final double[] h;
    private DataSet data;
    private double[][] _data;
    private double alpha;
    private double p;
    private TetradMatrix H;
    private TetradMatrix I;
    private ChiSquaredDistribution chisq = new ChiSquaredDistribution(new SynchronizedRandomGenerator(
            new Well44497b(193924L)), 1);
    private boolean bootstrap = true;
    private boolean approx = false;
    private Map<Node, Integer> hash;
    private double threshold = 0.01;
    private int nBootstraps = 5000;
    private double width = 1.0;
    private static List<Double> samples = new ArrayList<>();

    public KCI(DataSet data, double threshold) {
        this.data = DataUtils.standardizeData(data);
        this.data = data;
        this._data = this.data.getDoubleData()./*scalarMult(.3).*/transpose().toArray();
        this.N = data.getNumRows();
        this.I = TetradMatrix.identity(N);
        this.H = I.minus(TetradMatrix.ones(N, N).scalarMult(1.0 / N));
        this.alpha = threshold;
        this.p = -1;

        hash = new HashMap<>();

        for (int i = 0; i < getVariables().size(); i++) {
            hash.put(getVariables().get(i), i);
        }

        h = new double[data.getNumColumns()];

        for (int i = 0; i < data.getNumColumns(); i++) {
            h[i] = h(data.getVariables().get(i).toString());
        }
    }

    /**
     * Returns an Independence test for a subset of the variables.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true if the given independence question is judged true, false if not. The independence question is of the
     * form x _||_ y | z, z = <z1,...,zn>, where x, y, z1,...,zn are variables in the list returned by
     * getVariableNames().
     */
    public boolean isIndependent(Node x, Node y, List<Node> z) {
        boolean independent;

        if (z.isEmpty()) {
            independent = isIndependentUnconditional(x, y);
        } else {
            independent = isIndependentConditional(x, y, z);
        }

        if (independent) {
            System.out.println(SearchLogUtils.independenceFact(x, y, z) + " Independent");
        } else {
            System.out.println(SearchLogUtils.independenceFact(x, y, z));
        }

        return independent;
    }

    private boolean isIndependentUnconditional(Node x, Node y) {
        TetradMatrix kx = center(kernelMatrix(_data, x, null, getWidth()));
        TetradMatrix ky = center(kernelMatrix(_data, y, null, getWidth()));

        if (isApprox()) {
            return approx(kx, ky);
        } else {
            return theorem4(kx, ky);
        }
    }

    private boolean approx(TetradMatrix kx, TetradMatrix ky) {
        double trace = kx.times(ky).trace();
        double mean_appr = kx.trace() * ky.trace() / N;
        double var_appr = 2 * kx.times(kx).trace() * ky.times(ky).trace() / (N * N);//can optimize by not actually performing matrix multiplication
        double k_appr = mean_appr * mean_appr / var_appr;
        double theta_appr = var_appr / mean_appr;
        GammaDistribution g = new GammaDistribution(k_appr, theta_appr);
        double p_appr = 1.0 - g.cumulativeProbability(trace);
        p = p_appr;
        return p_appr > alpha;
    }

    private boolean isIndependentConditional(Node x, Node y, List<Node> z) {
        TetradMatrix Kx = center(kernelMatrix(_data, x, z, getWidth()));
        TetradMatrix Ky = center(kernelMatrix(_data, y, null, getWidth()));
        TetradMatrix KZ = kernelMatrix(_data, null, z, getWidth());

        KZ = I.minus(KZ.times((KZ.plus(I.scalarMult(1.0 / N)).inverse())));

        TetradMatrix kx = KZ.times(Kx).times(KZ);
        TetradMatrix ky = KZ.times(Ky).times(KZ);

        return theorem3(kx, ky);
    }

    private boolean theorem3(TetradMatrix kx, TetradMatrix ky) {

        try {
            double trace = kx.times(ky).trace();

            // Eigen decomposition of kx
            EigenDecomposition edx = new EigenDecomposition(symmetrized(kx));

            List<Double> evxAll = asList(edx.getRealEigenvalues());
            List<Integer> indx = series(evxAll.size()); // 1 2 3...
            indx.sort((o1, o2) -> Double.compare(evxAll.get(o2), evxAll.get(o1))); // Sorted downward by eigenvalue
            List<Integer> topXIndices = getTopIndices(evxAll, indx, threshold); // Get the ones above threshold.

            // VD
            TetradMatrix dx = new TetradMatrix(topXIndices.size(), topXIndices.size());

            for (int i = 0; i < topXIndices.size(); i++) {
                dx.set(i, i, Math.sqrt(evxAll.get(topXIndices.get(i))));
            }

            // Corresponding eigenvectors.
            TetradMatrix vx = new TetradMatrix(N, topXIndices.size());

            for (int i = 0; i < topXIndices.size(); i++) {
                RealVector t = edx.getEigenvector(topXIndices.get(i));
                vx.assignColumn(i, new TetradVector(t));
            }

            // Now, eigen decomposition of ky
            EigenDecomposition edy = new EigenDecomposition(symmetrized(ky));

            List<Double> evyAll = asList(edy.getRealEigenvalues());
            List<Integer> indy = series(evyAll.size()); // 1 2 3...
            indy.sort((o1, o2) -> Double.compare(evyAll.get(o2), evyAll.get(o1))); // Sorted downward by eigenvalue
            List<Integer> topYIndices = getTopIndices(evyAll, indy, threshold); // Get the ones above threshold.

            // square roots eigenvalues down the diagonal, like SVD
            TetradMatrix dy = new TetradMatrix(topYIndices.size(), topYIndices.size());

            for (int j = 0; j < topYIndices.size(); j++) {
                dy.set(j, j, Math.sqrt(evyAll.get(topYIndices.get(j))));
            }

            // Corresponding eigenvectors.
            TetradMatrix vy = new TetradMatrix(N, topYIndices.size());

            for (int i = 0; i < topYIndices.size(); i++) {
                RealVector t = edy.getEigenvector(topYIndices.get(i));
                vy.assignColumn(i, new TetradVector(t));
            }

            // VD
            TetradMatrix udx = vx.times(dx);
            TetradMatrix udy = vy.times(dy);

            final int prod = topXIndices.size() * topYIndices.size();
            TetradMatrix U = new TetradMatrix(N, prod);

            // stack
            for (int i = 0; i < topXIndices.size(); i++) {
                for (int j = 0; j < topYIndices.size(); j++) {
                    for (int k = 0; k < N; k++) {
                        U.set(k, i * topYIndices.size() + j, udx.get(k, i) * udy.get(k, j));
                    }
                }
            }

            // Whichever gives the smaller matrix.
            TetradMatrix uprod = prod > N ? U.times(U.transpose()) :  U.transpose().times(U);

            // Get top eigenvalues of that.
            EigenDecomposition edu = new EigenDecomposition(uprod.getRealMatrix());
            List<Double> evu = asList(edu.getRealEigenvalues());
            evu = getTopGuys(evu, threshold);

            // We're going to reuse the chisq samples.
            int sampleCount = -1;

            // Bootstrap.
            double sum = 0;

            for (int j = 0; j < getnBootstraps(); j++) {
                double s = 0.0;

                for (double lambdaStar : evu) {
                    s += lambdaStar * getChisqSample(++sampleCount);
                }

                if (s > trace) sum++;
            }

            this.p = sum / (double) getnBootstraps();
            return this.p > alpha;
        } catch (Exception e) {
            System.out.println("Eigenvalue didn't converge");
            p = 0.0;
            return true;
        }
    }

    private List<Integer> series(int size) {
        List<Integer> series = new ArrayList<>();
        for (int i = 0; i < size; i++) series.add(i);
        return series;
    }

    private TetradMatrix center(TetradMatrix K) {
        return H.times(K).times(H);
    }

    private double getChisqSample(int sampleCount) {
        for (int i = samples.size(); i <= sampleCount; i++) samples.add(chisq.sample());
        return samples.get(sampleCount);
    }

    private boolean theorem4(TetradMatrix kx, TetradMatrix ky) {
        double trace = kx.times(ky).trace();

        // Eigen decomposition of kx and ky.
        List<Double> evx;
        List<Double> evy;

        try {
            EigenDecomposition ed1 = new EigenDecomposition(symmetrized(kx));
            EigenDecomposition ed2 = new EigenDecomposition(symmetrized(ky));
            evx = asList(ed1.getRealEigenvalues());
            evy = asList(ed2.getRealEigenvalues());
        } catch (Exception e) {
            System.out.println("Eigenvalue didn't converge");
            p = 0.0;
            return true;
        }

        // Sorts the eigenvalues high to low.
        evx.sort((o1, o2) -> Double.compare(o2, o1));
        evy.sort((o1, o2) -> Double.compare(o2, o1));

        // Gets the guys in ev1 and ev2 that are above threshold.
        evx = getTopGuys(evx, threshold);
        evy = getTopGuys(evy, threshold);

        int sampleIndex = -1;

        // Calculate formula (9).
        double sum = 0;

        for (int j = 0; j < getnBootstraps(); j++) {
            double s = 0.0;

            for (double lambdax : evx) {
                for (double lambday : evy) {
                    s += lambdax * lambday * getChisqSample(++sampleIndex);
                }
            }

            if (s / (double) (evx.size() * evy.size()) > trace) sum++;
        }

        // Calculate p.
        p = sum / getnBootstraps();
        return p > alpha;
    }

    // Optimal bandwidth qsuggested by Bowman and Azzalini (1997) q.31,
    // using MAD.
    private double h(String x) {
        double[] xCol = _data[hash.get(data.getVariable(x))];
        double[] g = new double[xCol.length];
        double median = median(xCol);
        for (int j = 0; j < xCol.length; j++) g[j] = abs(xCol[j] - median);
        double mad = median(g);
        return (1.4826 * mad) * pow((4.0 / 3.0) / xCol.length, 0.2);
    }

    private boolean approxIndependent(TetradMatrix kx, TetradMatrix ky) {
        return approx(kx, ky);
    }

    private List<Double> getTopGuys(List<Double> allDoubles, double threshold) {
        double maxEig = allDoubles.get(0);

        List<Double> prodSelection = new ArrayList<>();

        for (double p : allDoubles) {
            if (p > maxEig * threshold) {
                prodSelection.add(p);
            }
        }

        return prodSelection;
    }

    private List<Integer> getTopIndices(List<Double> prod, List<Integer> allIndices, double threshold) {
        double maxEig = prod.get(allIndices.get(0));

        List<Integer> indices = new ArrayList<>();

        for (int i : allIndices) {
            if (prod.get(i) > maxEig * threshold) {
                indices.add(i);
            }
        }

        return indices;
    }

    private RealMatrix symmetrized(TetradMatrix kx) {
        return kx.plus(kx.transpose()).scalarMult(0.5).getRealMatrix();
    }

    private TetradMatrix kernelMatrix(double[][] _data, Node x, List<Node> z, double width) {

        List<Integer> _z = new ArrayList<>();

        if (x != null) {
            _z.add(hash.get(x));
        }

        if (z != null) {
            for (Node z2 : z) {
                _z.add(hash.get(z2));
            }
        }

        double h = 0;

        for (int c : _z) {
            if (this.h[c] > h) {
                h = this.h[c];
            }
        }

        h *= sqrt(_z.size());

        TetradMatrix result = new TetradMatrix(N, N);

        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                double d = distance(_data, _z, i, j);
                final double k = kernelGaussian(d, width, h);
                result.set(i, j, k);
                result.set(j, i, k);
            }
        }

        final double k = kernelGaussian(0, width, h);

        for (int i = 0; i < N; i++) {
            result.set(i, i, k);
        }

        return result;
    }

    private double kernelGaussian(double z, double width, double h) {
        z /= width * h;
        return Math.exp(-z * z);
    }

    // Euclidean distance.
    private double distance(double[][] data, List<Integer> cols, int i, int j) {
        double sum = 0.0;

        for (int col : cols) {
            double d = data[col][i] - data[col][j];

            if (!Double.isNaN(d)) {
                sum += d * d;
            }
        }

        return sqrt(sum);
    }

    /**
     * Returns true if the given independence question is judged true, false if not. The independence question is of the
     * form x _||_ y | z, z = <z1,...,zn>, where x, y, z1,...,zn are variables in the list returned by
     * getVariableNames().
     */
    public boolean isIndependent(Node x, Node y, Node... z) {
        LinkedList<Node> thez = new LinkedList<>();
        Collections.addAll(thez, z);
        return isIndependent(x, y, thez);
    }

    /**
     * Returns true if the given independence question is judged false, true if not. The independence question is of the
     * form x _||_ y | z, z = <z1,...,zn>, where x, y, z1,...,zn are variables in the list returned by
     * getVariableNames().
     */
    public boolean isDependent(Node x, Node y, List<Node> z) {
        return !isIndependent(x, y, z);
    }

    /**
     * Returns true if the given independence question is judged false, true if not. The independence question is of the
     * form x _||_ y | z, z = <z1,...,zn>, where x, y, z1,...,zn are variables in the list returned by
     * getVariableNames().
     */
    public boolean isDependent(Node x, Node y, Node... z) {
        LinkedList<Node> thez = new LinkedList<>();
        Collections.addAll(thez, z);
        return isDependent(x, y, thez);
    }

    /**
     * Returns the probability associated with the most recently executed independence test, of Double.NaN if p value is
     * not meaningful for tis test.
     */
    public double getPValue() {
        return p;
    }

    /**
     * Returns the list of variables over which this independence checker is capable of determinining independence
     * relations.
     */
    public List<Node> getVariables() {
        return data.getVariables();
    }

    /**
     * Returns the variable by the given name.
     */
    public Node getVariable(String name) {
        return data.getVariable(name);
    }

    /**
     * Returns the list of names for the variables in getNodesInEvidence.
     */
    public List<String> getVariableNames() {
        return data.getVariableNames();
    }

    /**
     * Returns true if y is determined the variable in z.
     */
    public boolean determines(List<Node> z, Node y) {

        return false;
    }

    /**
     * Returns the significance level of the independence test.
     *
     * @throws UnsupportedOperationException if there is no significance level.
     */
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the significance level.
     */
    public void setAlpha(double alpha2) {
        alpha = alpha2;
    }

    /**
     * '
     *
     * @return The data model for the independence test.
     */
    public DataModel getData() {
        return data;
    }


    public ICovarianceMatrix getCov() {
        throw new UnsupportedOperationException();
    }

    public List<DataSet> getDataSets() {
        LinkedList<DataSet> L = new LinkedList<>();
        L.add(data);
        return L;
    }

    public int getSampleSize() {
        return data.getNumRows();
    }

    public List<TetradMatrix> getCovMatrices() {
        throw new UnsupportedOperationException();
    }

    public double getScore() {
        return getAlpha() - getPValue();
    }

    public boolean isBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(boolean bootstrap) {
        this.bootstrap = bootstrap;
    }

    private boolean isApprox() {
        return approx;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        if (width <= 0) throw new IllegalStateException("Width must be > 0");
        this.width = width;
    }

    public int getnBootstraps() {
        return nBootstraps;
    }

    public void setnBootstraps(int nBootstraps) {
        if (nBootstraps < 1) throw new IllegalArgumentException("Num bootstraps should be >= 1: " + nBootstraps);
        this.nBootstraps = nBootstraps;
    }
}