package compression.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Random;
import org.junit.Test;

public class BitStreamTest {

    @Test
    public void writesMostSignificantBitFirstAndPadsWithZeros() {
        BitWriter writer = new BitWriter();
        writer.writeBits(0b101, 3);
        writer.writeBits(0b1, 1);
        writer.writeBits(0b11, 2);
        assertEquals(6, writer.bitLength());
        assertArrayEquals(new byte[] {(byte) 0b1011_1100}, writer.toByteArray());
    }

    @Test
    public void roundTripsRandomWidthsUpToSixtyFourBits() {
        Random random = new Random(42);
        int count = 5_000;
        long[] values = new long[count];
        int[] widths = new int[count];
        BitWriter writer = new BitWriter();
        for (int i = 0; i < count; i++) {
            widths[i] = 1 + random.nextInt(64);
            long value = random.nextLong();
            values[i] = widths[i] == 64 ? value : value & ((1L << widths[i]) - 1);
            writer.writeBits(values[i], widths[i]);
        }
        BitReader reader = new BitReader(writer.toByteArray(), 0);
        for (int i = 0; i < count; i++) {
            assertEquals("value " + i, values[i], reader.readBits(widths[i]));
        }
    }

    @Test
    public void readerStartsAtGivenByteOffset() {
        BitReader reader = new BitReader(new byte[] {(byte) 0xFF, (byte) 0b0100_0000}, 1);
        assertEquals(0, reader.readBit());
        assertEquals(1, reader.readBit());
    }

    @Test
    public void readingPastTheEndFails() {
        BitReader reader = new BitReader(new byte[] {0x01}, 0);
        reader.readBits(8);
        assertThrows(IllegalArgumentException.class, reader::readBit);
    }

    @Test
    public void rejectsInvalidWidths() {
        BitWriter writer = new BitWriter();
        assertThrows(IllegalArgumentException.class, () -> writer.writeBits(0, 0));
        assertThrows(IllegalArgumentException.class, () -> writer.writeBits(0, 65));
    }
}
