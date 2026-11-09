package pe.edu.ulima.compression.io;

import java.util.Arrays;

/**
 * Appends variable-width bit fields to a growable byte buffer, most significant bit first.
 *
 * <p>Pending bits live in a 64-bit accumulator and are flushed one byte at a time, so writing a
 * field of {@code w} bits costs O(1 + w/8). The final byte is padded with zeros.
 */
public final class BitWriter {

    private byte[] buffer;
    private int size;
    private long accumulator;
    private int pendingBits;

    public BitWriter() {
        this(64);
    }

    /** @param initialCapacity expected output size in bytes (a hint, the buffer grows as needed) */
    public BitWriter(int initialCapacity) {
        buffer = new byte[Math.max(16, initialCapacity)];
    }

    /** Writes up to 64 low-order bits of {@code value}. */
    public void writeBits(long value, int count) {
        if (count < 1 || count > 64) {
            throw new IllegalArgumentException("bit count must be in [1, 64]: " + count);
        }
        if (count > 56) {
            writeBits(value >>> 32, count - 32);
            writeBits(value, 32);
            return;
        }
        accumulator = (accumulator << count) | (value & ((1L << count) - 1));
        pendingBits += count;
        while (pendingBits >= 8) {
            pendingBits -= 8;
            put((byte) (accumulator >>> pendingBits));
        }
        accumulator &= (1L << pendingBits) - 1;
    }

    /** Number of bits written so far, excluding padding. */
    public long bitLength() {
        return (long) size * 8 + pendingBits;
    }

    /** Returns the written bits, zero-padded to a whole number of bytes. */
    public byte[] toByteArray() {
        byte[] out = Arrays.copyOf(buffer, size + (pendingBits > 0 ? 1 : 0));
        if (pendingBits > 0) {
            out[size] = (byte) (accumulator << (8 - pendingBits));
        }
        return out;
    }

    private void put(byte b) {
        if (size == buffer.length) {
            buffer = Arrays.copyOf(buffer, buffer.length * 2);
        }
        buffer[size++] = b;
    }
}
