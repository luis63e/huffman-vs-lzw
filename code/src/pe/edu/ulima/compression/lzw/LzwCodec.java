package pe.edu.ulima.compression.lzw;

import java.util.Arrays;
import pe.edu.ulima.compression.Codec;
import pe.edu.ulima.compression.io.BitReader;
import pe.edu.ulima.compression.io.BitWriter;

/**
 * Lempel-Ziv-Welch coding (Welch, 1984) with variable-width codes and dictionary reset.
 *
 * <p>Codes 0-255 are single bytes, 256 is {@code CLEAR} and new phrases start at 257. Codes are
 * written with the smallest width (9 to {@code maxBits} bits) able to represent every code that
 * could appear at that point. When the dictionary holds {@code 2^maxBits} entries, the encoder
 * emits {@code CLEAR} and both sides start over, which keeps the model adaptive.
 *
 * <p>Stream format: magic {@code 'L' 'Z'}, {@code maxBits} (1 byte), original length (4 bytes,
 * big-endian), then the MSB-first code stream.
 *
 * <p>Complexity, with n input bytes and a dictionary of D = 2^maxBits entries:
 * <ul>
 *   <li>Encoding: each input byte triggers one expected O(1) hash-table probe, so O(n) expected.
 *   <li>Decoding: each code is resolved through array lookups and every output byte is written
 *       exactly once, so O(n) worst case.
 *   <li>Extra space: O(D), independent of n.
 * </ul>
 */
public final class LzwCodec implements Codec {

    public static final int MIN_BITS = 9;
    public static final int MAX_BITS = 16;
    public static final int DEFAULT_BITS = 16;

    /** Header size in bytes: magic (2) + maxBits (1) + original length (4). */
    public static final int HEADER_BYTES = 7;

    static final int CLEAR = 256;
    static final int FIRST_FREE = 257;

    private static final byte MAGIC_0 = 'L';
    private static final byte MAGIC_1 = 'Z';

    private final int maxBits;

    public LzwCodec() {
        this(DEFAULT_BITS);
    }

    /** @param maxBits largest code width, which fixes the dictionary size at {@code 2^maxBits} */
    public LzwCodec(int maxBits) {
        if (maxBits < MIN_BITS || maxBits > MAX_BITS) {
            throw new IllegalArgumentException("maxBits must be in [" + MIN_BITS + ", " + MAX_BITS + "]: " + maxBits);
        }
        this.maxBits = maxBits;
    }

    public int maxBits() {
        return maxBits;
    }

    @Override
    public String name() {
        return maxBits == DEFAULT_BITS ? "LZW" : "LZW-" + maxBits;
    }

    @Override
    public byte[] encode(byte[] input) {
        BitWriter writer = new BitWriter(input.length / 2 + 16);
        compress(input, writer::writeBits);
        byte[] payload = writer.toByteArray();

        byte[] out = new byte[HEADER_BYTES + payload.length];
        out[0] = MAGIC_0;
        out[1] = MAGIC_1;
        out[2] = (byte) maxBits;
        out[3] = (byte) (input.length >>> 24);
        out[4] = (byte) (input.length >>> 16);
        out[5] = (byte) (input.length >>> 8);
        out[6] = (byte) input.length;
        System.arraycopy(payload, 0, out, HEADER_BYTES, payload.length);
        return out;
    }

    /** Returns the sequence of codes the encoder emits, for teaching and testing. */
    public int[] traceCodes(byte[] input) {
        int[][] codes = {new int[16]};
        int[] count = {0};
        compress(input, (code, width) -> {
            if (count[0] == codes[0].length) {
                codes[0] = Arrays.copyOf(codes[0], count[0] * 2);
            }
            codes[0][count[0]++] = (int) code;
        });
        return Arrays.copyOf(codes[0], count[0]);
    }

    @FunctionalInterface
    private interface CodeSink {
        void put(long code, int width);
    }

    /** Smallest width able to hold every code below {@code size}. */
    static int width(int size) {
        return Math.max(MIN_BITS, 32 - Integer.numberOfLeadingZeros(size - 1));
    }

    private void compress(byte[] input, CodeSink sink) {
        if (input.length == 0) {
            return;
        }
        int maxSize = 1 << maxBits;
        PhraseTable table = new PhraseTable(maxBits);
        int size = FIRST_FREE;
        int phrase = input[0] & 0xFF;
        for (int i = 1; i < input.length; i++) {
            int next = input[i] & 0xFF;
            int key = (phrase << 8) | next;
            int slot = table.find(key);
            if (table.isOccupied(slot)) {
                phrase = table.valueAt(slot); // extend the current phrase
                continue;
            }
            sink.put(phrase, width(size));
            if (size < maxSize) {
                table.insert(slot, key, size++);
            } else {
                sink.put(CLEAR, width(size));
                table.clear();
                size = FIRST_FREE;
            }
            phrase = next;
        }
        sink.put(phrase, width(size));
    }

    @Override
    public byte[] decode(byte[] encoded) {
        if (encoded.length < HEADER_BYTES || encoded[0] != MAGIC_0 || encoded[1] != MAGIC_1) {
            throw new IllegalArgumentException("not an LZW stream");
        }
        int streamBits = encoded[2];
        if (streamBits < MIN_BITS || streamBits > MAX_BITS) {
            throw new IllegalArgumentException("invalid maxBits in header: " + streamBits);
        }
        int length = ((encoded[3] & 0xFF) << 24) | ((encoded[4] & 0xFF) << 16)
                | ((encoded[5] & 0xFF) << 8) | (encoded[6] & 0xFF);
        if (length < 0) {
            throw new IllegalArgumentException("negative original length");
        }

        int maxSize = 1 << streamBits;
        int[] prefix = new int[maxSize];
        byte[] suffix = new byte[maxSize];
        byte[] firstByte = new byte[maxSize];
        int[] phraseLength = new int[maxSize];
        for (int c = 0; c < 256; c++) {
            suffix[c] = (byte) c;
            firstByte[c] = (byte) c;
            phraseLength[c] = 1;
        }

        byte[] out = new byte[length];
        BitReader reader = new BitReader(encoded, HEADER_BYTES);
        int size = FIRST_FREE;
        int previous = -1;
        int pos = 0;
        while (pos < length) {
            // The decoder builds each entry one code later than the encoder, hence size + 1.
            int code = (int) reader.readBits(width(previous < 0 ? size : Math.min(size + 1, maxSize)));
            if (code == CLEAR) {
                if (previous < 0) {
                    throw new IllegalArgumentException("unexpected CLEAR code");
                }
                size = FIRST_FREE;
                previous = -1;
                continue;
            }
            if (previous < 0) {
                if (code > 255) {
                    throw new IllegalArgumentException("invalid first code: " + code);
                }
                out[pos++] = (byte) code;
                previous = code;
                continue;
            }
            if (code > size || (code == size && size >= maxSize)) {
                throw new IllegalArgumentException("invalid code " + code + " (dictionary size " + size + ")");
            }
            if (size < maxSize) {
                // code == size is the KwKwK case: the new phrase starts with its own first byte.
                byte head = code < size ? firstByte[code] : firstByte[previous];
                prefix[size] = previous;
                suffix[size] = head;
                firstByte[size] = firstByte[previous];
                phraseLength[size] = phraseLength[previous] + 1;
                size++;
            }
            int n = phraseLength[code];
            if (n > length - pos) {
                throw new IllegalArgumentException("decoded data exceeds the declared length");
            }
            int c = code;
            for (int p = pos + n - 1; p >= pos; p--) {
                out[p] = suffix[c];
                c = prefix[c];
            }
            pos += n;
            previous = code;
        }
        return out;
    }

    /**
     * Open-addressing hash table from (phrase code, next byte) to phrase code. Capacity is twice the
     * dictionary size, so the load factor never exceeds 0.5 and probes are expected O(1).
     */
    private static final class PhraseTable {
        private final int[] keys; // key + 1, so 0 marks an empty slot
        private final int[] values;
        private final int mask;
        private final int shift;

        PhraseTable(int maxBits) {
            int capacity = 1 << (maxBits + 1);
            keys = new int[capacity];
            values = new int[capacity];
            mask = capacity - 1;
            shift = 32 - (maxBits + 1);
        }

        int find(int key) {
            int slot = (key * 0x9E3779B1) >>> shift; // Fibonacci hashing
            int stored = key + 1;
            while (keys[slot] != 0 && keys[slot] != stored) {
                slot = (slot + 1) & mask;
            }
            return slot;
        }

        boolean isOccupied(int slot) {
            return keys[slot] != 0;
        }

        int valueAt(int slot) {
            return values[slot];
        }

        void insert(int slot, int key, int value) {
            keys[slot] = key + 1;
            values[slot] = value;
        }

        void clear() {
            Arrays.fill(keys, 0);
        }
    }
}
