package sketches;

import msketch.*;
import org.apache.avro.generic.GenericData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tracks both the moments and the log-moments to better support
 * lognormal, pareto, etc... distributions
 */
public class HybridMomentSketch implements QuantileSketch{
    private int k = 5;
    private double tolerance = 1e-10;
    private boolean verbose = false;

    private double min;
    private double max;
    private double logMin;
    private double logMax;
    // Stores the normal moments and the log moments
    private double[] totalSums;

    private boolean usedLog;
    private double[] errors;

    @Override
    public String getName() {
        return "hmoments";
    }

    public HybridMomentSketch(double tolerance) {
        this.tolerance = tolerance;
    }

    @Override
    public int getSize() {
        return (Double.BYTES)*(4+totalSums.length);
    }

    @Override
    public double getSizeParam() {
        return k;
    }

    @Override
    public void setSizeParam(double sizeParam) {
        this.k = (int)sizeParam;
    }

    @Override
    public void setVerbose(boolean flag) {
        verbose = flag;
    }

    @Override
    public void initialize() {
        this.min = Double.MAX_VALUE;
        this.max = -Double.MAX_VALUE;
        this.logMin = Double.MAX_VALUE;
        this.logMax = -Double.MAX_VALUE;
        this.totalSums = new double[2*k];
    }

    @Override
    public void setCalcError(boolean flag) {
        return;
    }

    public void setStats(
            double[] powerSums,
            double[] logSums,
            double min,
            double max,
            double logMin,
            double logMax
    ) {
        k = powerSums.length;
        totalSums = new double[2*k];
        for (int i = 0; i < k; i++) {
            totalSums[i] = powerSums[i];
            totalSums[k+i] = logSums[i];
        }
        this.min = min;
        this.max = max;
        this.logMin = logMin;
        this.logMax = logMax;
    }

    @Override
    public void add(double[] data) {
        for (double x: data) {
            if (x < this.min) {
                this.min = x;
            } else if (x > this.max) {
                this.max = x;
            }
            double[] localSums = this.totalSums;
            localSums[0]++;
            double curPow = 1.0;
            int numPows = k;
            for (int i = 1; i < numPows; i++) {
                curPow *= x;
                localSums[i] += curPow;
            }

            if (x > 0.0) {
                double logX = Math.log(x);
                if (logX < this.logMin) {
                    this.logMin = logX;
                } else if (logX > this.logMax) {
                    this.logMax = logX;
                }
                localSums[numPows]++;
                curPow = 1.0;
                for (int i = 1; i < numPows; i++) {
                    curPow *= logX;
                    localSums[numPows+i] += curPow;
                }
            }
        }
    }

    @Override
    public QuantileSketch merge(ArrayList<QuantileSketch> sketches) {
        double mMin = this.min;
        double mMax = this.max;
        double mLogMin = this.logMin;
        double mLogMax = this.logMax;
        double[] mSums = this.totalSums;
        final int k2 = this.k * 2;

        for (QuantileSketch s : sketches) {
            HybridMomentSketch ms = (HybridMomentSketch) s;
            if (ms.min < mMin) {
                mMin = ms.min;
            }
            if (ms.max > mMax) {
                mMax = ms.max;
            }
            if (ms.logMin < mLogMin) {
                mLogMin = ms.logMin;
            }
            if (ms.logMax > mLogMax) {
                mLogMax = ms.logMax;
            }
            for (int i = 0; i < k2; i++) {
                mSums[i] += ms.totalSums[i];
            }
        }
        this.min = mMin;
        this.max = mMax;
        this.logMin = mLogMin;
        this.logMax = mLogMax;
        return this;
    }

    @Override
    public double[] getQuantiles(List<Double> pList) throws Exception {
        double[] powerSums = Arrays.copyOfRange(totalSums, 0, k);
        double[] powMoments = MathUtil.powerSumsToMoments(powerSums);
        double[] logSums = Arrays.copyOfRange(totalSums, k, 2*k);
        double[] logMoments = MathUtil.powerSumsToMoments(logSums);
        double totalCount = powerSums[0];
        double logCount = logSums[0];
        int m = pList.size();
        double[] ps = new double[m];
        for (int i = 0; i < m; i++) {
            ps[i] = pList.get(i);
        }

        ChebyshevMomentSolver solver = ChebyshevMomentSolver.fromPowerSums(
                min, max, powerSums
        );
        solver.setVerbose(verbose);
        double[] powQuantiles = new double[m];
        double[] powBoundSizes = new double[m];
        double powAvgBoundSize = 1;
        SimpleBoundSolver powBoundSolver = new SimpleBoundSolver(k);

        solver.solve(tolerance);
        boolean powConverged = solver.isConverged();
        if (powConverged) {
            powQuantiles = solver.estimateQuantiles(ps, min, max);
            if (verbose) {
                System.out.println("==Power==");
                System.out.println("Quantiles: "+Arrays.toString(powQuantiles));
            }
            powBoundSizes = powBoundSolver.solveBounds(powMoments, powQuantiles);
            powAvgBoundSize = MathUtil.arrayMean(powBoundSizes);
            if (verbose) {
                System.out.println("Avg Bound Size: " + powAvgBoundSize);
                System.out.println("Bounds: "+Arrays.toString(powBoundSizes));
            }
        }

        // Deal with Logs
        solver = ChebyshevMomentSolver.fromPowerSums(
                logMin, logMax, logSums
        );
        solver.setVerbose(verbose);
        double[] logQuantiles = new double[m];
        double[] logBoundSizes = new double[m];
        double logAvgBoundSize = 1;
        double logMissingFrac = (totalCount - logCount) / totalCount;

        int numNegPs = 0;
        double[] shiftedPs = new double[m];
        for (int i = 0; i < m; i++) {
            shiftedPs[i] = ps[i] - logMissingFrac;
            if (ps[i] < logMissingFrac) {
                numNegPs++;
            }
        }
        SimpleBoundSolver logBoundSolver = new SimpleBoundSolver(k);

        solver.solve(tolerance);
        boolean logConverged = solver.isConverged();
        if (logConverged) {
            logQuantiles = solver.estimateQuantiles(shiftedPs, logMin, logMax);
            if (verbose) {
                System.out.println("==Log==");
                System.out.println("Quantiles: "+Arrays.toString(logQuantiles));
            }
            logBoundSizes = logBoundSolver.solveBounds(logMoments, logQuantiles);
            for (int i = 0; i < numNegPs; i++) {
                logBoundSizes[i] = Math.abs(logMissingFrac);
            }
            logAvgBoundSize = MathUtil.arrayMean(logBoundSizes);
            if (verbose) {
                System.out.println("Avg Bound Size: " + logAvgBoundSize + " Omitted: "+logMissingFrac);
                System.out.println("Bounds: " + Arrays.toString(logBoundSizes));
            }
        }

        if (powConverged && logConverged) {
            usedLog = (logAvgBoundSize < powAvgBoundSize);
        } else if (!powConverged && logConverged) {
            usedLog = true;
        } else if (powConverged && !logConverged) {
            usedLog = false;
        } else {
            usedLog = false;
            throw new Exception("Did not converge");
        }

        if (usedLog) {
            errors = logBoundSolver.getMaxErrors(logMoments, logQuantiles, shiftedPs, logBoundSizes);
            for (int i = 0; i < logMissingFrac; i++) {
                errors[i] = logMissingFrac;
            }
            for (int i = 0; i < m; i++) {
                if (i < numNegPs) {
                    logQuantiles[i] = 0;
                } else {
                    logQuantiles[i] = Math.exp(logQuantiles[i]);
                }
            }
            return logQuantiles;
        } else {
            errors = powBoundSolver.getMaxErrors(powMoments, powQuantiles, ps, powBoundSizes);
            return powQuantiles;
        }
    }

    @Override
    public double[] getErrors() {
        return errors;
    }
}