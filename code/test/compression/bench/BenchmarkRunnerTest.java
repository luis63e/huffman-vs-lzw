package compression.bench;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BenchmarkRunnerTest {

    private static final String E1_HEADER = "n,h0,huff_enc_ms,huff_enc_ci,huff_dec_ms,huff_dec_ci,huff_bpb,"
            + "lzw_enc_ms,lzw_enc_ci,lzw_dec_ms,lzw_dec_ci,lzw_bpb,"
            + "huff_enc_nspb,huff_dec_nspb,lzw_enc_nspb,lzw_dec_nspb";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

    private int run(String... args) {
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);
        return BenchmarkRunner.run(args, out, err);
    }

    private String stderr() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    private File tinyCorpus() throws IOException {
        File corpus = tmp.newFolder("corpus");
        Path canterbury = Files.createDirectory(corpus.toPath().resolve("canterbury"));
        Files.writeString(canterbury.resolve("a.txt"), "abracadabra ".repeat(300));
        Files.writeString(canterbury.resolve("b_file.txt"), "the quick brown fox jumps over the lazy dog\n".repeat(100));
        return corpus;
    }

    @Test
    public void quickRunWritesAllResultFiles() throws IOException {
        File out = tmp.newFolder("out");
        int code = run("--out", out.getPath(), "--corpus", tinyCorpus().getPath(), "--quick");
        assertEquals(stderr(), 0, code);
        for (String name : new String[] {"e1_uniform.csv", "e1_skewed.csv", "e1_synthtext.csv", "fits.csv",
            "e2_entropy.csv", "e3_corpus.csv", "table_corpus.tex", "macros.tex", "environment.txt"}) {
            assertTrue(name, new File(out, name).isFile());
        }
        List<String> e1 = Files.readAllLines(out.toPath().resolve("e1_uniform.csv"));
        assertEquals(E1_HEADER, e1.get(0));
        assertEquals("quick sizes 2^10..2^14", 1 + 5, e1.size());
        assertTrue(e1.get(1).startsWith("1024,"));
        assertTrue(e1.get(5).startsWith("16384,"));

        List<String> e3 = Files.readAllLines(out.toPath().resolve("e3_corpus.csv"));
        assertEquals(1 + 2, e3.size());
        assertTrue(e3.get(1).startsWith("canterbury,a.txt,3600,"));
        assertTrue(e3.get(2).startsWith("canterbury,b_file.txt,4400,"));

        String table = Files.readString(out.toPath().resolve("table_corpus.tex"));
        assertTrue(table, table.contains("\\texttt{b\\_file.txt}"));

        String macros = Files.readString(out.toPath().resolve("macros.tex"));
        assertTrue(macros.contains("\\newcommand{\\resSlopeUniformHuffEnc}"));
        assertTrue(macros.contains("\\newcommand{\\resCorpusFiles}{2}"));
        assertFalse("no digits in macro names", macros.matches("(?s).*\\\\newcommand\\{\\\\[A-Za-z]*[0-9_].*"));
    }

    @Test
    public void onlyE2WritesOnlyTheEntropySweep() throws IOException {
        File out = tmp.newFolder("out");
        int code = run("--out", out.getPath(), "--corpus", tinyCorpus().getPath(), "--quick", "--only", "e2");
        assertEquals(stderr(), 0, code);
        assertTrue(new File(out, "e2_entropy.csv").isFile());
        List<String> e2 = Files.readAllLines(out.toPath().resolve("e2_entropy.csv"));
        assertEquals(1 + 8, e2.size());
        String[] files = out.list();
        for (String name : files) {
            assertTrue("unexpected " + name, name.equals("e2_entropy.csv")
                    || name.equals("environment.txt") || name.equals("macros.tex"));
        }
    }

    @Test
    public void unknownArgumentPrintsUsage() {
        assertEquals(2, run("--out", tmp.getRoot().getPath(), "--corpus", tmp.getRoot().getPath(), "--bogus"));
        assertTrue(stderr().contains("Usage"));
    }

    @Test
    public void missingOutIsRejected() {
        assertEquals(2, run("--corpus", tmp.getRoot().getPath(), "--quick"));
        assertTrue(stderr().contains("Usage"));
    }
}
