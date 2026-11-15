package compression.io;

/** Reads variable-width bit fields written by {@link BitWriter}, most significant bit first. */
public final class BitReader {

    private final byte[] data;
    private int position;
    private long accumulator;
    private int availableBits;

    /**
     * @param data   source bytes
     * @param offset index of the first byte holding bit-stream data (after any header)
     */
    public BitReader(byte[] data, int offset) {
        if (offset < 0 || offset > data.length) {
            throw new IllegalArgumentException("offset out of range: " + offset);
        }
        this.data = data;
        this.position = offset;
    }

    /** Reads a single bit (0 or 1). */
    public int readBit() {
        if (availableBits == 0) {
            refill();
        }
        availableBits--;
        return (int) (accumulator >>> availableBits) & 1;
    }

    /**
     * Reads {@code count} bits as an unsigned value.
     *
     * @throws IllegalArgumentException if the stream ends before {@code count} bits are available
     */
    public long readBits(int count) {
        if (count < 1 || count > 64) {
            throw new IllegalArgumentException("bit count must be in [1, 64]: " + count);
        }
        if (count > 56) {
            long high = readBits(count - 32);
            return (high << 32) | readBits(32);
        }
        while (availableBits < count) {
            refill();
        }
        availableBits -= count;
        return (accumulator >>> availableBits) & ((1L << count) - 1);
    }

    private void refill() {
        if (position >= data.length) {
            throw new IllegalArgumentException("truncated bit stream");
        }
        accumulator = (accumulator << 8) | (data[position++] & 0xFF);
        availableBits += 8;
    }
}
