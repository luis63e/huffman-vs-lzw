package compression.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MainTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String... args) {
        return Main.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @Test
    public void compressesAndDecompressesFilesWithEitherAlgorithm() throws Exception {
        byte[] original = "abracadabra ".repeat(500).getBytes(StandardCharsets.US_ASCII);
        Path input = tmp.newFile("in.txt").toPath();
        Files.write(input, original);
        for (String algorithm : new String[] {"huffman", "lzw"}) {
            Path packed = tmp.getRoot().toPath().resolve("packed." + algorithm);
            Path restored = tmp.getRoot().toPath().resolve("restored." + algorithm);
            assertEquals(0, run("compress", algorithm, input.toString(), packed.toString()));
            assertEquals(0, run("decompress", packed.toString(), restored.toString()));
            assertArrayEquals(original, Files.readAllBytes(restored));
        }
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("bits/byte"));
    }

    @Test
    public void statsReportsEntropyAndBothAlgorithms() throws Exception {
        Path input = tmp.newFile("s.txt").toPath();
        Files.write(input, "to be or not to be".repeat(100).getBytes(StandardCharsets.US_ASCII));
        assertEquals(0, run("stats", input.toString()));
        String text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text, text.contains("Entropy"));
        assertTrue(text, text.contains("Huffman"));
        assertTrue(text, text.contains("LZW"));
    }

    @Test
    public void rejectsUnknownAlgorithmWithUsageExitCode() throws Exception {
        Path input = tmp.newFile("x").toPath();
        assertEquals(2, run("compress", "zip", input.toString(), input + ".out"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("unknown algorithm: zip"));
        assertTrue(Files.notExists(Path.of(input + ".out")));
    }

    @Test
    public void rejectsCorruptArchiveWithErrorExitCode() throws Exception {
        Path bogus = tmp.newFile("bogus").toPath();
        Files.write(bogus, new byte[] {9, 9, 9});
        assertEquals(1, run("decompress", bogus.toString(), bogus + ".out"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("unrecognized format"));
    }

    @Test
    public void runsDemoWithUsageWhenCalledWithoutArguments() {
        assertEquals(0, run());
        String text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text, text.contains("TOBEORNOTTOBEORTOBEORNOT"));
        assertTrue(text, text.contains("Usage"));
    }

    @Test
    public void rejectsUnknownCommandWithUsageExitCode() {
        assertEquals(2, run("explode"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("unknown command: explode"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Usage"));
    }
}
