package compression.app;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import compression.Codec;
import compression.bench.Stats;
import compression.huffman.HuffmanCodec;
import compression.lzw.LzwCodec;

/** Command-line entry point: compress, decompress, and inspect files with Huffman or LZW. */
public final class Main {

    static final String USAGE = String.join(System.lineSeparator(),
            "Usage:",
            "  java -jar HuffmanVsLZW.jar compress <huffman|lzw|lzw-N> <input> <output>   (N = 9..16)",
            "  java -jar HuffmanVsLZW.jar decompress <input> <output>",
            "  java -jar HuffmanVsLZW.jar stats <input>",
            "  java -jar HuffmanVsLZW.jar demo",
            "Benchmarks: ant benchmark (see README.md)");

    private Main() {
    }

    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    /** Runs one command and returns the process exit code: 0 ok, 1 runtime error, 2 usage error. */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            demo(out);
            out.println();
            out.println(USAGE);
            return 0;
        }
        try {
            switch (args[0]) {
                case "compress":
                    requireArgs(args, 4);
                    return compress(args[1], Path.of(args[2]), Path.of(args[3]), out, err);
                case "decompress":
                    requireArgs(args, 3);
                    return decompress(Path.of(args[1]), Path.of(args[2]), out, err);
                case "stats":
                    requireArgs(args, 2);
                    return stats(Path.of(args[1]), out);
                case "demo":
                    demo(out);
                    return 0;
                default:
                    err.println("unknown command: " + args[0]);
                    err.println(USAGE);
                    return 2;
            }
        } catch (UsageException e) {
            err.println(e.getMessage());
            err.println(USAGE);
            return 2;
        } catch (IOException e) {
            err.println("error: " + e.getMessage());
            return 1;
        } catch (IllegalArgumentException e) {
            err.println("error: " + e.getMessage());
            return 1;
        }
    }

    private static int compress(String algorithm, Path input, Path output, PrintStream out, PrintStream err)
            throws IOException, UsageException {
        Codec codec = codecFor(algorithm);
        byte[] data = Files.readAllBytes(input);
        long start = System.nanoTime();
        byte[] encoded = codec.encode(data);
        double ms = (System.nanoTime() - start) / 1e6;
        Files.write(output, encoded);
        out.printf(Locale.ROOT, "%s: %d -> %d bytes (%.3f bits/byte, %.1f%% saved) in %.1f ms%n",
                codec.name(), data.length, encoded.length, bitsPerByte(encoded.length, data.length),
                saved(encoded.length, data.length), ms);
        return 0;
    }

    private static int decompress(Path input, Path output, PrintStream out, PrintStream err) throws IOException {
        byte[] encoded = Files.readAllBytes(input);
        Codec codec = detect(encoded);
        if (codec == null) {
            err.println("error: unrecognized format (expected a Huffman 'HF' or LZW 'LZ' stream)");
            return 1;
        }
        long start = System.nanoTime();
        byte[] data = codec.decode(encoded);
        double ms = (System.nanoTime() - start) / 1e6;
        Files.write(output, data);
        out.printf(Locale.ROOT, "%s: %d -> %d bytes in %.1f ms%n", codec.name(), encoded.length, data.length, ms);
        return 0;
    }

    private static int stats(Path input, PrintStream out) throws IOException {
        byte[] data = Files.readAllBytes(input);
        out.printf(Locale.ROOT, "File: %s (%d bytes)%n", input.getFileName(), data.length);
        out.printf(Locale.ROOT, "Entropy (order 0): %.4f bits/byte%n", Stats.entropy(data));
        for (Codec codec : new Codec[] {new HuffmanCodec(), new LzwCodec()}) {
            byte[] encoded = codec.encode(data);
            if (!Arrays.equals(data, codec.decode(encoded))) {
                throw new IllegalStateException(codec.name() + " failed to round-trip");
            }
            out.printf(Locale.ROOT, "%-8s %10d bytes  %.4f bits/byte  %.1f%% saved%n", codec.name(),
                    encoded.length, bitsPerByte(encoded.length, data.length), saved(encoded.length, data.length));
        }
        return 0;
    }

    /** Small worked examples used in the oral presentation. */
    private static void demo(PrintStream out) {
        String text = "TOBEORNOTTOBEORTOBEORNOT";
        out.println("LZW demo (Welch, 1984): " + text);
        out.println("  codes: " + Arrays.toString(new LzwCodec().traceCodes(text.getBytes(StandardCharsets.US_ASCII))));
        out.println("  (0-255 = literal bytes, 256 = CLEAR, 257+ = learned phrases)");

        String sample = "abracadabra";
        byte[] bytes = sample.getBytes(StandardCharsets.US_ASCII);
        long[] freq = HuffmanCodec.frequencies(bytes);
        int[] lengths = HuffmanCodec.codeLengths(freq);
        long[] codes = HuffmanCodec.canonicalCodes(lengths);
        out.println("Huffman demo (canonical codes): " + sample);
        for (int s = 0; s < 256; s++) {
            if (lengths[s] > 0) {
                String bits = Long.toBinaryString(codes[s]);
                bits = "0".repeat(lengths[s] - bits.length()) + bits;
                out.printf(Locale.ROOT, "  '%c' freq=%d code=%s%n", (char) s, freq[s], bits);
            }
        }
    }

    private static Codec codecFor(String algorithm) throws UsageException {
        String name = algorithm.toLowerCase(Locale.ROOT);
        if (name.equals("huffman")) {
            return new HuffmanCodec();
        }
        if (name.equals("lzw")) {
            return new LzwCodec();
        }
        if (name.startsWith("lzw-")) {
            try {
                return new LzwCodec(Integer.parseInt(name.substring(4)));
            } catch (IllegalArgumentException e) {
                throw new UsageException("invalid LZW width: " + algorithm + " (use lzw-9 .. lzw-16)");
            }
        }
        throw new UsageException("unknown algorithm: " + algorithm);
    }

    private static Codec detect(byte[] encoded) {
        if (encoded.length >= 3 && encoded[0] == 'H' && encoded[1] == 'F') {
            return new HuffmanCodec();
        }
        if (encoded.length >= 3 && encoded[0] == 'L' && encoded[1] == 'Z'
                && encoded[2] >= LzwCodec.MIN_BITS && encoded[2] <= LzwCodec.MAX_BITS) {
            return new LzwCodec(encoded[2]);
        }
        return null;
    }

    private static double bitsPerByte(long encoded, long original) {
        return original == 0 ? 0 : 8.0 * encoded / original;
    }

    private static double saved(long encoded, long original) {
        return original == 0 ? 0 : 100.0 * (1 - (double) encoded / original);
    }

    private static void requireArgs(String[] args, int count) throws UsageException {
        if (args.length != count) {
            throw new UsageException("wrong number of arguments for " + args[0]);
        }
    }

    private static final class UsageException extends Exception {
        private static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }
}
