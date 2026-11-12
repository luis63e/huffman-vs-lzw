package compression.bench;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class StatsTest {

    @Test
    public void summarizesSamples() {
        Stats.Summary s = Stats.summarize(new double[] {4, 1, 3, 2, 5});
        assertEquals(3.0, s.median(), 1e-12);
        assertEquals(3.0, s.mean(), 1e-12);
        assertEquals(Math.sqrt(2.5), s.stdDev(), 1e-12);
        // t(0.975, df=4) = 2.776
        assertEquals(2.776 * Math.sqrt(2.5) / Math.sqrt(5), s.ci95(), 1e-9);
        assertEquals(5, s.count());
    }

    @Test
    public void medianOfEvenCountAveragesMiddleValues() {
        assertEquals(2.5, Stats.summarize(new double[] {1, 2, 3, 4}).median(), 1e-12);
    }

    @Test
    public void largeSamplesUseNormalQuantile() {
        double[] samples = new double[100];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = i % 2;
        }
        Stats.Summary s = Stats.summarize(samples);
        assertEquals(1.96 * s.stdDev() / 10, s.ci95(), 1e-3);
    }

    @Test
    public void rejectsTooFewSamples() {
        assertThrows(IllegalArgumentException.class, () -> Stats.summarize(new double[] {1}));
    }

    @Test
    public void fitsPowerLawExponentOnLogLogScale() {
        double[] n = {1e3, 1e4, 1e5, 1e6};
        double[] linear = {2e3, 2e4, 2e5, 2e6};
        double[] quadratic = {1e6, 1e8, 1e10, 1e12};
        assertEquals(1.0, Stats.logLogSlope(n, linear).slope(), 1e-12);
        assertEquals(2.0, Stats.logLogSlope(n, quadratic).slope(), 1e-12);
        assertEquals(1.0, Stats.logLogSlope(n, linear).r2(), 1e-12);
    }

    @Test
    public void computesOrderZeroEntropy() {
        byte[] constant = new byte[100];
        assertEquals(0.0, Stats.entropy(constant), 1e-12);

        byte[] twoSymbols = new byte[100];
        for (int i = 0; i < 50; i++) {
            twoSymbols[i] = 1;
        }
        assertEquals(1.0, Stats.entropy(twoSymbols), 1e-12);

        byte[] all = new byte[256 * 10];
        for (int i = 0; i < all.length; i++) {
            all[i] = (byte) i;
        }
        assertEquals(8.0, Stats.entropy(all), 1e-12);
        assertEquals(0.0, Stats.entropy(new byte[0]), 1e-12);
    }
}
