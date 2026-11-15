package compression.huffman;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.Test;

public class HuffmanCodecTest {

    private final HuffmanCodec codec = new HuffmanCodec();

    private void assertRoundTrip(byte[] input) {
        byte[] encoded = codec.encode(input);
        assertArrayEquals(input, codec.decode(encoded));
    }

    @Test
    public void roundTripsEmptyInput() {
        assertRoundTrip(new byte[0]);
    }

    @Test
    public void roundTripsSingleByte() {
        assertRoundTrip(new byte[] {7});
    }

    @Test
    public void roundTripsSingleRepeatedSymbolWithOneBitPerSymbol() {
        byte[] input = new byte[1000];
        byte[] encoded = codec.encode(input);
        assertArrayEquals(input, codec.decode(encoded));
        assertEquals(HuffmanCodec.HEADER_BYTES + 1000 / 8, encoded.length);
    }

    @Test
    public void roundTripsEveryByteValue() {
        byte[] input = new byte[256 * 3];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) i;
        }
        assertRoundTrip(input);
    }

    @Test
    public void roundTripsRandomAndSkewedData() {
        Random random = new Random(7);
        byte[] uniform = new byte[100_000];
        random.nextBytes(uniform);
        assertRoundTrip(uniform);

        byte[] skewed = new byte[100_000];
        for (int i = 0; i < skewed.length; i++) {
            skewed[i] = (byte) Math.min(255, (int) (-Math.log(1 - random.nextDouble()) * 6));
        }
        assertRoundTrip(skewed);
    }

    @Test
    public void roundTripsText() {
        assertRoundTrip("the quick brown fox jumps over the lazy dog; THE QUICK BROWN FOX!"
                .repeat(50).getBytes(StandardCharsets.UTF_8));
    }

    /** Classic example from Cormen et al.: frequencies 45,13,12,16,9,5 cost 224 bits. */
    @Test
    public void computesOptimalCodeLengthsForTextbookExample() {
        long[] freq = new long[256];
        freq['a'] = 45;
        freq['b'] = 13;
        freq['c'] = 12;
        freq['d'] = 16;
        freq['e'] = 9;
        freq['f'] = 5;
        int[] lengths = HuffmanCodec.codeLengths(freq);
        assertEquals(1, lengths['a']);
        assertEquals(3, lengths['b']);
        assertEquals(3, lengths['c']);
        assertEquals(3, lengths['d']);
        assertEquals(4, lengths['e']);
        assertEquals(4, lengths['f']);
        assertEquals(0, lengths['z']);

        StringBuilder text = new StringBuilder();
        String symbols = "abcdef";
        int[] counts = {45, 13, 12, 16, 9, 5};
        for (int s = 0; s < symbols.length(); s++) {
            text.append(String.valueOf(symbols.charAt(s)).repeat(counts[s]));
        }
        byte[] encoded = codec.encode(text.toString().getBytes(StandardCharsets.US_ASCII));
        assertEquals(HuffmanCodec.HEADER_BYTES + 224 / 8, encoded.length);
    }

    @Test
    public void codeLengthsSatisfyKraftEquality() {
        Random random = new Random(3);
        long[] freq = new long[256];
        for (int i = 0; i < freq.length; i++) {
            freq[i] = 1 + random.nextInt(10_000);
        }
        int[] lengths = HuffmanCodec.codeLengths(freq);
        double kraft = 0;
        for (int length : lengths) {
            kraft += Math.pow(2, -length);
        }
        assertEquals(1.0, kraft, 1e-12);
    }

    @Test
    public void canonicalCodesArePrefixFreeAndOrderedByLength() {
        long[] freq = new long[256];
        long a = 1, b = 1;
        for (int i = 0; i < 40; i++) { // Fibonacci weights force a maximally deep tree
            freq[i] = a;
            long next = a + b;
            a = b;
            b = next;
        }
        int[] lengths = HuffmanCodec.codeLengths(freq);
        long[] codes = HuffmanCodec.canonicalCodes(lengths);
        assertEquals(39, java.util.Arrays.stream(lengths).max().getAsInt());
        for (int x = 0; x < 256; x++) {
            for (int y = 0; y < 256; y++) {
                if (x == y || lengths[x] == 0 || lengths[y] == 0 || lengths[x] > lengths[y]) {
                    continue;
                }
                long prefix = codes[y] >>> (lengths[y] - lengths[x]);
                assertFalse("code " + x + " is a prefix of " + y, prefix == codes[x]);
            }
        }
    }

    @Test
    public void rejectsCorruptInput() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[] {1, 2, 3}));
        byte[] encoded = codec.encode("hello huffman".getBytes(StandardCharsets.US_ASCII));
        byte[] truncated = java.util.Arrays.copyOf(encoded, encoded.length - 1);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(truncated));
        encoded[0] ^= 0x55;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded));
    }

    @Test
    public void exposesAlgorithmName() {
        assertTrue(codec.name().startsWith("Huffman"));
    }
}
