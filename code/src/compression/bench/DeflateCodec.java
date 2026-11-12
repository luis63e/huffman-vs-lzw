package compression.bench;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import compression.Codec;

/**
 * Reference point only: DEFLATE (LZ77 + Huffman, RFC 1951) from the JDK's zlib binding, level 6.
 * It is not one of the compared algorithms; it shows how far a production combination of both
 * ideas goes beyond each one alone.
 */
public final class DeflateCodec implements Codec {

    @Override
    public String name() {
        return "DEFLATE";
    }

    @Override
    public byte[] encode(byte[] input) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            deflater.setInput(input);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(input.length / 3 + 64);
            out.write(input.length >>> 24);
            out.write(input.length >>> 16);
            out.write(input.length >>> 8);
            out.write(input.length);
            byte[] buffer = new byte[1 << 16];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    @Override
    public byte[] decode(byte[] encoded) {
        if (encoded.length < 4) {
            throw new IllegalArgumentException("not a DEFLATE stream");
        }
        int length = ((encoded[0] & 0xFF) << 24) | ((encoded[1] & 0xFF) << 16)
                | ((encoded[2] & 0xFF) << 8) | (encoded[3] & 0xFF);
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(encoded, 4, encoded.length - 4);
            byte[] out = new byte[length];
            int pos = 0;
            while (pos < length) {
                int n = inflater.inflate(out, pos, length - pos);
                if (n == 0 && (inflater.finished() || inflater.needsInput())) {
                    throw new IllegalArgumentException("truncated DEFLATE stream");
                }
                pos += n;
            }
            return out;
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("corrupt DEFLATE stream", e);
        } finally {
            inflater.end();
        }
    }
}
