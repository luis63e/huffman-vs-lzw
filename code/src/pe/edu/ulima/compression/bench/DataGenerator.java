package pe.edu.ulima.compression.bench;

import java.util.Arrays;
import java.util.Random;

/** Deterministic synthetic inputs that isolate the properties each algorithm exploits. */
public final class DataGenerator {

    /** Synthetic sources used in the scaling experiment. */
    public enum Source {
        /** I.i.d. uniform bytes: about 8 bits/byte, incompressible for both algorithms. */
        UNIFORM("uniform"),
        /** I.i.d. geometric symbols: skewed frequencies but no repeated phrases (favors Huffman). */
        SKEWED("skewed"),
        /** Words from a Zipf-distributed vocabulary: many repeated phrases (favors LZW). */
        REPETITIVE_TEXT("synthtext");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Relative frequencies of English letters a-z (percent). */
    private static final double[] ENGLISH = {
        8.2, 1.5, 2.8, 4.3, 12.7, 2.2, 2.0, 6.1, 7.0, 0.15, 0.77, 4.0, 2.4,
        6.7, 7.5, 1.9, 0.095, 6.0, 6.3, 9.1, 2.8, 0.98, 2.4, 0.15, 2.0, 0.074,
    };
    private static final int VOCABULARY = 2000;
    private static final double ZIPF_EXPONENT = 1.1;
    private static final double GEOMETRIC_P = 0.15;

    private DataGenerator() {
    }

    public static byte[] generate(Source source, int n, long seed) {
        Random random = new Random(seed);
        switch (source) {
            case UNIFORM: {
                byte[] out = new byte[n];
                random.nextBytes(out);
                return out;
            }
            case SKEWED:
                return geometric(n, random);
            case REPETITIVE_TEXT:
                return zipfText(n, random);
            default:
                throw new AssertionError(source);
        }
    }

    /** I.i.d. symbols drawn uniformly from {0, ..., k-1}: entropy log2(k) bits/byte. */
    public static byte[] uniformAlphabet(int n, int k, long seed) {
        if (k < 1 || k > 256) {
            throw new IllegalArgumentException("k must be in [1, 256]: " + k);
        }
        Random random = new Random(seed);
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) random.nextInt(k);
        }
        return out;
    }

    private static byte[] geometric(int n, Random random) {
        double logQ = Math.log(1 - GEOMETRIC_P);
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            int symbol = (int) (Math.log(1 - random.nextDouble()) / logQ);
            out[i] = (byte) Math.min(symbol, 255);
        }
        return out;
    }

    private static byte[] zipfText(int n, Random random) {
        double[] letterCdf = cumulative(ENGLISH);
        byte[][] words = new byte[VOCABULARY][];
        for (int w = 0; w < VOCABULARY; w++) {
            byte[] word = new byte[2 + random.nextInt(8)];
            for (int i = 0; i < word.length; i++) {
                word[i] = (byte) ('a' + sample(letterCdf, random));
            }
            words[w] = word;
        }
        double[] zipf = new double[VOCABULARY];
        for (int r = 0; r < VOCABULARY; r++) {
            zipf[r] = 1 / Math.pow(r + 1, ZIPF_EXPONENT);
        }
        double[] wordCdf = cumulative(zipf);

        byte[] out = new byte[n];
        int pos = 0;
        int wordsInSentence = 0;
        while (pos < n) {
            byte[] word = words[sample(wordCdf, random)];
            int len = Math.min(word.length, n - pos);
            System.arraycopy(word, 0, out, pos, len);
            pos += len;
            if (pos < n) {
                wordsInSentence++;
                boolean endSentence = wordsInSentence > 8 && random.nextInt(6) == 0;
                out[pos++] = (byte) (endSentence ? '\n' : ' ');
                if (endSentence) {
                    wordsInSentence = 0;
                }
            }
        }
        return out;
    }

    private static double[] cumulative(double[] weights) {
        double[] cdf = new double[weights.length];
        double total = Arrays.stream(weights).sum();
        double running = 0;
        for (int i = 0; i < weights.length; i++) {
            running += weights[i] / total;
            cdf[i] = running;
        }
        cdf[cdf.length - 1] = 1.0;
        return cdf;
    }

    private static int sample(double[] cdf, Random random) {
        int index = Arrays.binarySearch(cdf, random.nextDouble());
        return index >= 0 ? index : Math.min(-index - 1, cdf.length - 1);
    }
}
