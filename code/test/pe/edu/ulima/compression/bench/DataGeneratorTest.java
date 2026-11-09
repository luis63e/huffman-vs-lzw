package pe.edu.ulima.compression.bench;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DataGeneratorTest {

    @Test
    public void generatorsAreDeterministicForAGivenSeed() {
        for (DataGenerator.Source source : DataGenerator.Source.values()) {
            byte[] a = DataGenerator.generate(source, 10_000, 1);
            byte[] b = DataGenerator.generate(source, 10_000, 1);
            assertEquals(source.name(), 10_000, a.length);
            assertArrayEquals(source.name(), a, b);
        }
    }

    @Test
    public void sourcesHaveTheExpectedEntropy() {
        int n = 1 << 20;
        double uniform = Stats.entropy(DataGenerator.generate(DataGenerator.Source.UNIFORM, n, 1));
        double skewed = Stats.entropy(DataGenerator.generate(DataGenerator.Source.SKEWED, n, 1));
        double words = Stats.entropy(DataGenerator.generate(DataGenerator.Source.REPETITIVE_TEXT, n, 1));
        assertEquals(8.0, uniform, 0.01);
        assertTrue("skewed entropy " + skewed, skewed > 3.0 && skewed < 5.5);
        assertTrue("text entropy " + words, words > 3.5 && words < 5.0);
    }

    @Test
    public void alphabetSourceUsesExactlyKSymbols() {
        for (int k : new int[] {2, 16, 256}) {
            byte[] data = DataGenerator.uniformAlphabet(1 << 18, k, 5);
            double expected = Math.log(k) / Math.log(2);
            assertEquals("k=" + k, expected, Stats.entropy(data), 0.01);
            for (byte b : data) {
                assertTrue((b & 0xFF) < k);
            }
        }
    }
}
