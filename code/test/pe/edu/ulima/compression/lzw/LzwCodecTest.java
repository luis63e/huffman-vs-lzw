package pe.edu.ulima.compression.lzw;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.Test;

public class LzwCodecTest {

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static void assertRoundTrip(LzwCodec codec, byte[] input) {
        assertArrayEquals(input, codec.decode(codec.encode(input)));
    }

    /**
     * Welch's classic example. With 256 = CLEAR, new phrases start at 257, so the textbook codes
     * 256, 258, 260, 265, 259, 261, 263 appear shifted by one.
     */
    @Test
    public void emitsTextbookCodeSequence() {
        int[] codes = new LzwCodec().traceCodes(ascii("TOBEORNOTTOBEORTOBEORNOT"));
        int[] expected = {'T', 'O', 'B', 'E', 'O', 'R', 'N', 'O', 'T', 257, 259, 261, 266, 260, 262, 264};
        assertArrayEquals(expected, codes);
    }

    @Test
    public void roundTripsEmptyAndSingleByte() {
        LzwCodec codec = new LzwCodec();
        assertRoundTrip(codec, new byte[0]);
        assertRoundTrip(codec, new byte[] {(byte) 0xFF});
    }

    /** "aaaa..." forces the decoder to see a code it has not built yet (the KwKwK case). */
    @Test
    public void handlesCodeNotYetInDictionary() {
        LzwCodec codec = new LzwCodec();
        byte[] input = ascii("a".repeat(1000));
        int[] codes = codec.traceCodes(ascii("aaaa"));
        assertArrayEquals(new int[] {'a', 257, 'a'}, codes);
        assertRoundTrip(codec, input);
        assertRoundTrip(codec, ascii("abababababababababab"));
    }

    @Test
    public void roundTripsEveryWidthIncludingDictionaryResets() {
        Random random = new Random(11);
        byte[] noise = new byte[200_000];
        random.nextBytes(noise);
        byte[] text = ascii("It was the best of times, it was the worst of times, ".repeat(4000));
        for (int maxBits = LzwCodec.MIN_BITS; maxBits <= LzwCodec.MAX_BITS; maxBits++) {
            LzwCodec codec = new LzwCodec(maxBits);
            assertRoundTrip(codec, noise);
            assertRoundTrip(codec, text);
        }
    }

    @Test
    public void roundTripsEveryByteValue() {
        byte[] input = new byte[256 * 4];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (i * 7);
        }
        assertRoundTrip(new LzwCodec(), input);
    }

    @Test
    public void compressesRepetitiveInputWell() {
        byte[] input = ascii("ab".repeat(50_000));
        byte[] encoded = new LzwCodec().encode(input);
        assertTrue("encoded size " + encoded.length, encoded.length < input.length / 50);
    }

    @Test
    public void rejectsInvalidConfigurationAndCorruptInput() {
        assertThrows(IllegalArgumentException.class, () -> new LzwCodec(8));
        assertThrows(IllegalArgumentException.class, () -> new LzwCodec(17));

        LzwCodec codec = new LzwCodec();
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[] {1, 2}));
        byte[] encoded = codec.encode(ascii("TOBEORNOTTOBEORTOBEORNOT"));
        byte[] truncated = java.util.Arrays.copyOf(encoded, encoded.length - 2);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(truncated));

        // First code after the header refers to an entry that cannot exist yet.
        byte[] bogus = encoded.clone();
        bogus[LzwCodec.HEADER_BYTES] = (byte) 0xFF;
        bogus[LzwCodec.HEADER_BYTES + 1] = (byte) 0xFF;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(bogus));
    }

    @Test
    public void streamRecordsItsDictionaryWidth() {
        byte[] encoded = new LzwCodec(12).encode(ascii("hello"));
        assertArrayEquals(ascii("hello"), new LzwCodec(16).decode(encoded));
        assertEquals("LZW-12", new LzwCodec(12).name());
        assertEquals("LZW", new LzwCodec().name());
    }
}
