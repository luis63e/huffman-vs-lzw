package compression.huffman;

import java.util.PriorityQueue;
import compression.Codec;
import compression.io.BitReader;
import compression.io.BitWriter;

/**
 * Static (two-pass) canonical Huffman coding over the byte alphabet (Huffman, 1952).
 *
 * <p>Stream format: magic {@code 'H' 'F'}, original length (4 bytes, big-endian), 256 code
 * lengths (1 byte each, 0 = unused symbol), then the MSB-first bit stream. Storing only code
 * lengths is enough because canonical codes are rebuilt deterministically from them, as in
 * DEFLATE (RFC 1951).
 *
 * <p>Complexity, with n input bytes and an alphabet of size sigma &lt;= 256:
 * <ul>
 *   <li>Encoding: O(n) frequency count + O(sigma log sigma) tree construction with a binary heap
 *       + O(n) emission of codes, i.e. O(n + sigma log sigma), which is O(n) for a fixed alphabet.
 *   <li>Decoding: canonical decoding reads one bit per step, so it costs O(total encoded bits) =
 *       O(n * L), where L is the average code length (at most 8 bits plus one for this data).
 *   <li>Extra space: O(sigma) for the tree and code tables.
 * </ul>
 */
public final class HuffmanCodec implements Codec {

    /** Alphabet size: every byte value is a symbol. */
    public static final int ALPHABET = 256;

    /** Header size in bytes: magic (2) + original length (4) + code lengths (256). */
    public static final int HEADER_BYTES = 2 + 4 + ALPHABET;

    /** Longest code that fits in a {@code long}; never reached for inputs below 2^31 bytes. */
    static final int MAX_CODE_LENGTH = 63;

    private static final byte MAGIC_0 = 'H';
    private static final byte MAGIC_1 = 'F';

    @Override
    public String name() {
        return "Huffman";
    }

    @Override
    public byte[] encode(byte[] input) {
        long[] freq = frequencies(input);
        int[] lengths = codeLengths(freq);
        long[] codes = canonicalCodes(lengths);

        BitWriter writer = new BitWriter(input.length / 2 + 16);
        for (byte b : input) {
            int symbol = b & 0xFF;
            writer.writeBits(codes[symbol], lengths[symbol]);
        }
        byte[] payload = writer.toByteArray();

        byte[] out = new byte[HEADER_BYTES + payload.length];
        out[0] = MAGIC_0;
        out[1] = MAGIC_1;
        writeInt(out, 2, input.length);
        for (int s = 0; s < ALPHABET; s++) {
            out[6 + s] = (byte) lengths[s];
        }
        System.arraycopy(payload, 0, out, HEADER_BYTES, payload.length);
        return out;
    }

    @Override
    public byte[] decode(byte[] encoded) {
        if (encoded.length < HEADER_BYTES || encoded[0] != MAGIC_0 || encoded[1] != MAGIC_1) {
            throw new IllegalArgumentException("not a Huffman stream");
        }
        int originalLength = readInt(encoded, 2);
        if (originalLength < 0) {
            throw new IllegalArgumentException("negative original length");
        }
        int[] lengths = new int[ALPHABET];
        for (int s = 0; s < ALPHABET; s++) {
            lengths[s] = encoded[6 + s] & 0xFF;
        }
        CanonicalTable table = CanonicalTable.of(lengths);
        if (originalLength > 0 && table.maxLength == 0) {
            throw new IllegalArgumentException("non-empty stream without codes");
        }

        byte[] out = new byte[originalLength];
        BitReader reader = new BitReader(encoded, HEADER_BYTES);
        for (int i = 0; i < originalLength; i++) {
            out[i] = (byte) table.decodeSymbol(reader);
        }
        return out;
    }

    /** Counts occurrences of each byte value: O(n). */
    public static long[] frequencies(byte[] input) {
        long[] freq = new long[ALPHABET];
        for (byte b : input) {
            freq[b & 0xFF]++;
        }
        return freq;
    }

    /**
     * Computes optimal (minimum-redundancy) code lengths with Huffman's greedy algorithm: repeatedly
     * merge the two lightest trees using a binary min-heap. O(sigma log sigma).
     *
     * <p>Ties are broken by node creation order, so the result is deterministic. A single used
     * symbol receives a 1-bit code.
     *
     * @param freq frequency of each of the 256 symbols
     * @return code length per symbol, 0 for symbols that never occur
     */
    public static int[] codeLengths(long[] freq) {
        if (freq.length != ALPHABET) {
            throw new IllegalArgumentException("expected " + ALPHABET + " frequencies");
        }
        int maxNodes = 2 * ALPHABET - 1;
        long[] weight = new long[maxNodes];
        int[] parent = new int[maxNodes];
        PriorityQueue<Integer> heap = new PriorityQueue<>(ALPHABET,
                (x, y) -> weight[x] != weight[y] ? Long.compare(weight[x], weight[y]) : Integer.compare(x, y));

        int used = 0;
        for (int s = 0; s < ALPHABET; s++) {
            if (freq[s] < 0) {
                throw new IllegalArgumentException("negative frequency for symbol " + s);
            }
            if (freq[s] > 0) {
                weight[s] = freq[s];
                heap.add(s);
                used++;
            }
        }
        int[] lengths = new int[ALPHABET];
        if (used == 0) {
            return lengths;
        }
        if (used == 1) {
            lengths[heap.poll()] = 1;
            return lengths;
        }

        int next = ALPHABET; // internal nodes get indices 256, 257, ...
        while (heap.size() > 1) {
            int a = heap.poll();
            int b = heap.poll();
            weight[next] = weight[a] + weight[b];
            parent[a] = next;
            parent[b] = next;
            heap.add(next++);
        }

        // Parents always have larger indices than children, so one descending pass assigns depths.
        int root = next - 1;
        int[] depth = new int[maxNodes];
        for (int node = root - 1; node >= ALPHABET; node--) {
            depth[node] = depth[parent[node]] + 1;
        }
        for (int s = 0; s < ALPHABET; s++) {
            if (freq[s] > 0) {
                lengths[s] = depth[parent[s]] + 1;
                if (lengths[s] > MAX_CODE_LENGTH) {
                    throw new IllegalStateException("code length exceeds " + MAX_CODE_LENGTH);
                }
            }
        }
        return lengths;
    }

    /**
     * Assigns canonical codes: shorter codes first, and within one length in increasing symbol
     * order (the DEFLATE construction, RFC 1951 section 3.2.2). O(sigma + L_max).
     */
    public static long[] canonicalCodes(int[] lengths) {
        int maxLength = 0;
        for (int length : lengths) {
            maxLength = Math.max(maxLength, length);
        }
        int[] countPerLength = new int[maxLength + 1];
        for (int length : lengths) {
            if (length > 0) {
                countPerLength[length]++;
            }
        }
        long[] nextCode = new long[maxLength + 1];
        long code = 0;
        for (int length = 1; length <= maxLength; length++) {
            code = (code + countPerLength[length - 1]) << 1;
            nextCode[length] = code;
        }
        long[] codes = new long[lengths.length];
        for (int s = 0; s < lengths.length; s++) {
            if (lengths[s] > 0) {
                codes[s] = nextCode[lengths[s]]++;
            }
        }
        return codes;
    }

    /** Decoding tables for canonical codes (Moffat and Turpin, 1997). */
    private static final class CanonicalTable {
        final int[] countPerLength;
        final int[] symbolsByCode;
        final int maxLength;

        private CanonicalTable(int[] countPerLength, int[] symbolsByCode, int maxLength) {
            this.countPerLength = countPerLength;
            this.symbolsByCode = symbolsByCode;
            this.maxLength = maxLength;
        }

        static CanonicalTable of(int[] lengths) {
            int maxLength = 0;
            for (int length : lengths) {
                if (length > MAX_CODE_LENGTH) {
                    throw new IllegalArgumentException("code length too large: " + length);
                }
                maxLength = Math.max(maxLength, length);
            }
            int[] count = new int[maxLength + 1];
            int used = 0;
            for (int length : lengths) {
                if (length > 0) {
                    count[length]++;
                    used++;
                }
            }
            // Reject over-subscribed code sets (Kraft sum greater than one).
            long left = 1;
            for (int length = 1; length <= maxLength; length++) {
                left = Math.min(left << 1, ALPHABET + 1L) - count[length];
                if (left < 0) {
                    throw new IllegalArgumentException("over-subscribed code lengths");
                }
            }
            int[] offset = new int[maxLength + 2];
            for (int length = 1; length <= maxLength; length++) {
                offset[length + 1] = offset[length] + count[length];
            }
            int[] symbols = new int[used];
            for (int s = 0; s < lengths.length; s++) {
                if (lengths[s] > 0) {
                    symbols[offset[lengths[s]]++] = s;
                }
            }
            return new CanonicalTable(count, symbols, maxLength);
        }

        /** Reads one code bit by bit: O(code length). */
        int decodeSymbol(BitReader reader) {
            long code = 0;
            long first = 0;
            int index = 0;
            for (int length = 1; length <= maxLength; length++) {
                code |= reader.readBit();
                int count = countPerLength[length];
                if (code - first < count) {
                    return symbolsByCode[index + (int) (code - first)];
                }
                index += count;
                first = (first + count) << 1;
                code <<= 1;
            }
            throw new IllegalArgumentException("invalid Huffman code");
        }
    }

    private static void writeInt(byte[] out, int at, int value) {
        out[at] = (byte) (value >>> 24);
        out[at + 1] = (byte) (value >>> 16);
        out[at + 2] = (byte) (value >>> 8);
        out[at + 3] = (byte) value;
    }

    private static int readInt(byte[] in, int at) {
        return ((in[at] & 0xFF) << 24) | ((in[at + 1] & 0xFF) << 16)
                | ((in[at + 2] & 0xFF) << 8) | (in[at + 3] & 0xFF);
    }
}
