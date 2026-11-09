package pe.edu.ulima.compression.bench;

import java.util.Arrays;

/** Descriptive statistics, confidence intervals, power-law fits and empirical entropy. */
public final class Stats {

    /** Two-sided 95% Student t quantiles for 1..30 degrees of freedom. */
    private static final double[] T_975 = {
        12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
        2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086,
        2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042,
    };
    private static final double Z_975 = 1.96;

    private Stats() {
    }

    /** Summary of repeated measurements; {@code ci95} is the half-width of the 95% CI of the mean. */
    public record Summary(int count, double mean, double median, double stdDev, double ci95, double min, double max) {
    }

    /** Least-squares fit of {@code ln y = slope * ln x + intercept}. */
    public record Fit(double slope, double intercept, double r2) {
    }

    public static Summary summarize(double[] samples) {
        int n = samples.length;
        if (n < 2) {
            throw new IllegalArgumentException("at least two samples are required");
        }
        double[] sorted = samples.clone();
        Arrays.sort(sorted);
        double median = n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
        double mean = Arrays.stream(samples).sum() / n;
        double squares = 0;
        for (double x : samples) {
            squares += (x - mean) * (x - mean);
        }
        double stdDev = Math.sqrt(squares / (n - 1));
        double t = n - 1 <= T_975.length ? T_975[n - 2] : Z_975;
        return new Summary(n, mean, median, stdDev, t * stdDev / Math.sqrt(n), sorted[0], sorted[n - 1]);
    }

    /**
     * Fits a power law y = c * x^k. For an algorithm with complexity Theta(n^k), the slope of
     * log(time) against log(n) tends to k (McGeoch, 2012).
     */
    public static Fit logLogSlope(double[] x, double[] y) {
        if (x.length != y.length || x.length < 2) {
            throw new IllegalArgumentException("need two equally sized series with at least two points");
        }
        int n = x.length;
        double sx = 0, sy = 0, sxx = 0, sxy = 0, syy = 0;
        for (int i = 0; i < n; i++) {
            double lx = Math.log(x[i]);
            double ly = Math.log(y[i]);
            sx += lx;
            sy += ly;
            sxx += lx * lx;
            sxy += lx * ly;
            syy += ly * ly;
        }
        double slope = (n * sxy - sx * sy) / (n * sxx - sx * sx);
        double intercept = (sy - slope * sx) / n;
        double ssTot = syy - sy * sy / n;
        double ssRes = 0;
        for (int i = 0; i < n; i++) {
            double residual = Math.log(y[i]) - (slope * Math.log(x[i]) + intercept);
            ssRes += residual * residual;
        }
        double r2 = ssTot == 0 ? 1.0 : Math.max(0, 1 - ssRes / ssTot);
        return new Fit(slope, intercept, Math.abs(ssRes) < 1e-20 ? 1.0 : r2);
    }

    /** Order-0 empirical entropy in bits per byte (Shannon, 1948). */
    public static double entropy(byte[] data) {
        if (data.length == 0) {
            return 0;
        }
        long[] freq = new long[256];
        for (byte b : data) {
            freq[b & 0xFF]++;
        }
        double h = 0;
        for (long f : freq) {
            if (f > 0) {
                double p = (double) f / data.length;
                h -= p * Math.log(p) / Math.log(2);
            }
        }
        return h;
    }
}
