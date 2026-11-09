package pe.edu.ulima.compression.bench;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import pe.edu.ulima.compression.Codec;
import pe.edu.ulima.compression.huffman.HuffmanCodec;
import pe.edu.ulima.compression.lzw.LzwCodec;

/**
 * Runs the experimental campaign and writes CSV/LaTeX results.
 *
 * <p>Methodology (Georges et al., 2007; McGeoch, 2012): every configuration is first checked for a
 * lossless round trip, then run for W warm-up iterations so the JIT reaches steady state, then
 * timed for R iterations with encode and decode measured separately. Reported times are medians,
 * with the half-width of the 95% confidence interval of the mean as dispersion.
 *
 * <ul>
 *   <li>E1 scaling: time vs input size for synthetic sources and a real text prefix.</li>
 *   <li>E2 entropy sweep: bits/byte and time vs alphabet size k (entropy log2 k).</li>
 *   <li>E3 corpus: Canterbury and Silesia files, with DEFLATE as a reference.</li>
 *   <li>E4 LZW dictionary size: effect of the maximum code width.</li>
 * </ul>
 */
public final class BenchmarkRunner {

    static final long SEED = 2026;
    private static final List<String> EXPERIMENTS = List.of("e1", "e2", "e3", "e4");
    private static final int[] E2_ALPHABETS = {2, 4, 8, 16, 32, 64, 128, 256};
    private static final String[] NUMBER_WORDS = {
        "Two", "Four", "Eight", "Sixteen", "ThirtyTwo", "SixtyFour", "OneTwentyEight", "TwoFiftySix",
    };
    private static final String USAGE = "Usage: BenchmarkRunner --out <dir> --corpus <dir> [--quick]"
            + " [--only e1,e2,e3,e4]";

    /** Sink for codec outputs so the JIT cannot eliminate the measured work. */
    static volatile long blackhole;

    private final Path outDir;
    private final Path corpusDir;
    private final boolean quick;
    private final PrintStream out;
    private final PrintStream err;

    /** Macro name to value, in insertion order. */
    private final Map<String, String> macros = new LinkedHashMap<>();

    private BenchmarkRunner(Path outDir, Path corpusDir, boolean quick, PrintStream out, PrintStream err) {
        this.outDir = outDir;
        this.corpusDir = corpusDir;
        this.quick = quick;
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    /** Parses {@code args}, runs the selected experiments and returns the process exit code. */
    static int run(String[] args, PrintStream out, PrintStream err) {
        String outArg = null;
        String corpusArg = null;
        boolean quick = false;
        Set<String> only = new LinkedHashSet<>(EXPERIMENTS);
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            boolean hasValue = i + 1 < args.length;
            if (arg.equals("--out") && hasValue) {
                outArg = args[++i];
            } else if (arg.equals("--corpus") && hasValue) {
                corpusArg = args[++i];
            } else if (arg.equals("--quick")) {
                quick = true;
            } else if (arg.equals("--only") && hasValue) {
                only = new LinkedHashSet<>(Arrays.asList(args[++i].toLowerCase().split(",")));
                if (!EXPERIMENTS.containsAll(only)) {
                    err.println("Unknown experiment in --only: " + args[i]);
                    err.println(USAGE);
                    return 2;
                }
            } else {
                err.println("Unknown or incomplete argument: " + arg);
                err.println(USAGE);
                return 2;
            }
        }
        if (outArg == null || corpusArg == null) {
            err.println("Both --out and --corpus are required.");
            err.println(USAGE);
            return 2;
        }
        try {
            Path outDir = Paths.get(outArg);
            Files.createDirectories(outDir);
            new BenchmarkRunner(outDir, Paths.get(corpusArg), quick, out, err).execute(only);
            return 0;
        } catch (IOException e) {
            err.println("I/O error: " + e.getMessage());
            return 1;
        }
    }

    private void execute(Set<String> only) throws IOException {
        environment();
        warmUp();
        if (only.contains("e1")) {
            fits(scaling());
        }
        if (only.contains("e2")) {
            entropySweep();
        }
        if (only.contains("e3")) {
            corpus();
        }
        if (only.contains("e4")) {
            lzwBits();
        }
        ResultWriter tex = ResultWriter.text(outDir.resolve("macros.tex"))
                .line("% Generated by BenchmarkRunner; do not edit.");
        macros.forEach((name, value) -> tex.line("\\newcommand{\\" + name + "}{" + value + "}"));
        tex.write();
    }

    // ---------------------------------------------------------------- measurement

    /** One measured configuration: compressed size plus encode/decode time summaries (ms). */
    record Measurement(int n, long encodedBytes, Stats.Summary enc, Stats.Summary dec) {

        double bpb() {
            return 8.0 * encodedBytes / n;
        }

        double encNsPerByte() {
            return enc.median() * 1e6 / n;
        }

        double decNsPerByte() {
            return dec.median() * 1e6 / n;
        }

        double encMbs() {
            return n / 1e6 / (enc.median() / 1e3);
        }

        double decMbs() {
            return n / 1e6 / (dec.median() / 1e3);
        }
    }

    int warmups(int n) {
        return quick ? 1 : n <= (1 << 20) ? 5 : n <= (1 << 24) ? 3 : 1;
    }

    int repetitions(int n) {
        return quick ? 3 : n <= (1 << 20) ? 21 : n <= (1 << 24) ? 11 : 7;
    }

    private Measurement measure(Codec codec, byte[] data) {
        System.gc();
        byte[] encoded = codec.encode(data);
        if (!Arrays.equals(codec.decode(encoded), data)) {
            throw new IllegalStateException(codec.name() + " failed the round trip on " + data.length + " bytes");
        }
        for (int i = 0; i < warmups(data.length); i++) {
            consume(codec.decode(codec.encode(data)));
        }
        int r = repetitions(data.length);
        double[] enc = new double[r];
        double[] dec = new double[r];
        for (int i = 0; i < r; i++) {
            long t0 = System.nanoTime();
            byte[] e = codec.encode(data);
            long t1 = System.nanoTime();
            byte[] d = codec.decode(e);
            long t2 = System.nanoTime();
            consume(e);
            consume(d);
            enc[i] = (t1 - t0) / 1e6;
            dec[i] = (t2 - t1) / 1e6;
        }
        return new Measurement(data.length, encoded.length, Stats.summarize(enc), Stats.summarize(dec));
    }

    private static void consume(byte[] bytes) {
        blackhole += bytes.length + (bytes.length > 0 ? bytes[bytes.length - 1] : 0);
    }

    private void progress(String prefix, String codec, Measurement m) {
        out.println(prefix + " " + codec + " enc=" + ResultWriter.num(m.enc().median(), 2) + "ms dec="
                + ResultWriter.num(m.dec().median(), 2) + "ms bpb=" + ResultWriter.num(m.bpb(), 3));
    }

    /** Global JIT warm-up so the first E1 points are not measured in the interpreter. */
    private void warmUp() {
        int n = quick ? 1 << 16 : 1 << 22;
        int iterations = quick ? 1 : 3;
        Codec[] codecs = {new HuffmanCodec(), new LzwCodec(16), new DeflateCodec()};
        for (DataGenerator.Source source : DataGenerator.Source.values()) {
            byte[] data = DataGenerator.generate(source, n, SEED);
            for (Codec codec : codecs) {
                for (int i = 0; i < iterations; i++) {
                    consume(codec.decode(codec.encode(data)));
                }
            }
        }
        out.println("Warm-up done (" + n + " bytes x " + iterations + " iterations per codec and source)");
    }

    // ---------------------------------------------------------------- E1 scaling

    /** E1 result for one size. */
    record ScalingPoint(int n, double h0, Measurement huff, Measurement lzw) {
    }

    private int minExponent() {
        return quick ? 10 : 14;
    }

    private Map<String, List<ScalingPoint>> scaling() throws IOException {
        Map<String, List<ScalingPoint>> results = new LinkedHashMap<>();
        int maxExp = quick ? 14 : 24;
        for (DataGenerator.Source source : DataGenerator.Source.values()) {
            byte[] full = DataGenerator.generate(source, 1 << maxExp, SEED);
            results.put(source.label(), scaling(source.label(), full, maxExp));
        }
        Path dickens = corpusDir.resolve("silesia").resolve("dickens");
        if (Files.isRegularFile(dickens)) {
            byte[] full = Files.readAllBytes(dickens);
            int maxExp2 = quick ? 14 : 23;
            while (maxExp2 > minExponent() && (1 << maxExp2) > full.length) {
                maxExp2--;
            }
            if ((1 << maxExp2) <= full.length) {
                results.put("dickens", scaling("dickens", full, maxExp2));
            }
        } else {
            err.println("Warning: " + dickens + " not found; skipping E1 dickens");
        }
        return results;
    }

    private List<ScalingPoint> scaling(String label, byte[] full, int maxExp) throws IOException {
        Codec huffman = new HuffmanCodec();
        Codec lzw = new LzwCodec(16);
        ResultWriter csv = ResultWriter.csv(outDir.resolve("e1_" + label + ".csv"),
                "n", "h0", "huff_enc_ms", "huff_enc_ci", "huff_dec_ms", "huff_dec_ci", "huff_bpb",
                "lzw_enc_ms", "lzw_enc_ci", "lzw_dec_ms", "lzw_dec_ci", "lzw_bpb",
                "huff_enc_nspb", "huff_dec_nspb", "lzw_enc_nspb", "lzw_dec_nspb");
        List<ScalingPoint> points = new ArrayList<>();
        for (int e = minExponent(); e <= maxExp; e++) {
            byte[] data = Arrays.copyOf(full, 1 << e);
            String prefix = "E1 " + label + " n=" + data.length;
            Measurement h = measure(huffman, data);
            progress(prefix, "huffman", h);
            Measurement l = measure(lzw, data);
            progress(prefix, "lzw", l);
            ScalingPoint p = new ScalingPoint(data.length, Stats.entropy(data), h, l);
            points.add(p);
            csv.row(p.n(), ResultWriter.num(p.h0(), 4),
                    ms(h.enc().median()), ms(h.enc().ci95()), ms(h.dec().median()), ms(h.dec().ci95()),
                    ResultWriter.num(h.bpb(), 4),
                    ms(l.enc().median()), ms(l.enc().ci95()), ms(l.dec().median()), ms(l.dec().ci95()),
                    ResultWriter.num(l.bpb(), 4),
                    ResultWriter.num(h.encNsPerByte(), 3), ResultWriter.num(h.decNsPerByte(), 3),
                    ResultWriter.num(l.encNsPerByte(), 3), ResultWriter.num(l.decNsPerByte(), 3));
        }
        csv.write();
        return points;
    }

    private static String ms(double value) {
        return ResultWriter.num(value, 4);
    }

    private void fits(Map<String, List<ScalingPoint>> results) throws IOException {
        ResultWriter csv = ResultWriter.csv(outDir.resolve("fits.csv"),
                "source", "algorithm", "operation", "slope", "r2", "nspb_at_max");
        int minN = quick ? 0 : 1 << 16;
        for (Map.Entry<String, List<ScalingPoint>> entry : results.entrySet()) {
            List<ScalingPoint> points = entry.getValue().stream().filter(p -> p.n() >= minN)
                    .collect(Collectors.toList());
            if (points.size() < 2) {
                err.println("Warning: fewer than two E1 points for " + entry.getKey() + "; no fit");
                continue;
            }
            for (String algorithm : new String[] {"huffman", "lzw"}) {
                for (String operation : new String[] {"encode", "decode"}) {
                    double[] x = new double[points.size()];
                    double[] y = new double[points.size()];
                    for (int i = 0; i < x.length; i++) {
                        Measurement m = algorithm.equals("huffman") ? points.get(i).huff() : points.get(i).lzw();
                        x[i] = points.get(i).n();
                        y[i] = (operation.equals("encode") ? m.enc() : m.dec()).median();
                    }
                    Stats.Fit fit = Stats.logLogSlope(x, y);
                    Measurement last = algorithm.equals("huffman")
                            ? points.get(points.size() - 1).huff() : points.get(points.size() - 1).lzw();
                    double nspb = operation.equals("encode") ? last.encNsPerByte() : last.decNsPerByte();
                    csv.row(entry.getKey(), algorithm, operation, ResultWriter.num(fit.slope(), 4),
                            ResultWriter.num(fit.r2(), 4), ResultWriter.num(nspb, 3));
                    String suffix = capitalize(entry.getKey()) + (algorithm.equals("huffman") ? "Huff" : "Lzw")
                            + (operation.equals("encode") ? "Enc" : "Dec");
                    macros.put("resSlope" + suffix, ResultWriter.num(fit.slope(), 2));
                    macros.put("resRtwo" + suffix, ResultWriter.num(fit.r2(), 3));
                    macros.put("resNspb" + suffix, ResultWriter.num(nspb, 2));
                }
            }
        }
        csv.write();
    }

    // ---------------------------------------------------------------- E2 entropy sweep

    private void entropySweep() throws IOException {
        int n = quick ? 1 << 14 : 1 << 23;
        Codec huffman = new HuffmanCodec();
        Codec lzw = new LzwCodec(16);
        ResultWriter csv = ResultWriter.csv(outDir.resolve("e2_entropy.csv"),
                "k", "h0", "huff_bpb", "lzw_bpb", "huff_enc_ms", "huff_enc_ci", "huff_dec_ms", "huff_dec_ci",
                "lzw_enc_ms", "lzw_enc_ci", "lzw_dec_ms", "lzw_dec_ci");
        for (int i = 0; i < E2_ALPHABETS.length; i++) {
            int k = E2_ALPHABETS[i];
            byte[] data = DataGenerator.uniformAlphabet(n, k, SEED);
            String prefix = "E2 k=" + k + " n=" + n;
            Measurement h = measure(huffman, data);
            progress(prefix, "huffman", h);
            Measurement l = measure(lzw, data);
            progress(prefix, "lzw", l);
            csv.row(k, ResultWriter.num(Stats.entropy(data), 4),
                    ResultWriter.num(h.bpb(), 4), ResultWriter.num(l.bpb(), 4),
                    ms(h.enc().median()), ms(h.enc().ci95()), ms(h.dec().median()), ms(h.dec().ci95()),
                    ms(l.enc().median()), ms(l.enc().ci95()), ms(l.dec().median()), ms(l.dec().ci95()));
            String word = NUMBER_WORDS[i];
            macros.put("resEntropyHuffBpbK" + word, ResultWriter.num(h.bpb(), 2));
            macros.put("resEntropyLzwBpbK" + word, ResultWriter.num(l.bpb(), 2));
            macros.put("resEntropyHuffEncMsK" + word, ResultWriter.num(h.enc().median(), 2));
            macros.put("resEntropyHuffDecMsK" + word, ResultWriter.num(h.dec().median(), 2));
            macros.put("resEntropyLzwEncMsK" + word, ResultWriter.num(l.enc().median(), 2));
            macros.put("resEntropyLzwDecMsK" + word, ResultWriter.num(l.dec().median(), 2));
        }
        csv.write();
    }

    // ---------------------------------------------------------------- E3 corpus

    private List<Path> corpusFiles(String corpus) throws IOException {
        Path dir = corpusDir.resolve(corpus);
        if (!Files.isDirectory(dir)) {
            err.println("Warning: corpus directory " + dir + " not found; skipping");
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
        }
    }

    private void corpus() throws IOException {
        Codec huffman = new HuffmanCodec();
        Codec lzw = new LzwCodec(16);
        Codec deflate = new DeflateCodec();
        ResultWriter csv = ResultWriter.csv(outDir.resolve("e3_corpus.csv"),
                "corpus", "file", "bytes", "h0", "huff_bpb", "lzw_bpb", "deflate_bpb",
                "huff_enc_mbs", "huff_dec_mbs", "lzw_enc_mbs", "lzw_dec_mbs", "deflate_enc_mbs", "deflate_dec_mbs");
        ResultWriter table = ResultWriter.text(outDir.resolve("table_corpus.tex"));
        long totalBytes = 0;
        long huffBytes = 0;
        long lzwBytes = 0;
        long deflateBytes = 0;
        int files = 0;
        int lzwWins = 0;
        boolean firstGroup = true;
        for (String corpus : new String[] {"canterbury", "silesia"}) {
            List<Path> paths = corpusFiles(corpus);
            if (paths.isEmpty()) {
                continue;
            }
            if (!firstGroup) {
                table.line("\\midrule");
            }
            firstGroup = false;
            for (Path path : paths) {
                byte[] data = Files.readAllBytes(path);
                if (data.length == 0) {
                    err.println("Warning: " + path + " is empty; skipping");
                    continue;
                }
                String name = path.getFileName().toString();
                String prefix = "E3 " + corpus + "/" + name + " n=" + data.length;
                Measurement h = measure(huffman, data);
                progress(prefix, "huffman", h);
                Measurement l = measure(lzw, data);
                progress(prefix, "lzw", l);
                Measurement d = measure(deflate, data);
                progress(prefix, "deflate", d);
                double h0 = Stats.entropy(data);
                csv.row(corpus, name, data.length, ResultWriter.num(h0, 4),
                        ResultWriter.num(h.bpb(), 4), ResultWriter.num(l.bpb(), 4), ResultWriter.num(d.bpb(), 4),
                        ResultWriter.num(h.encMbs(), 3), ResultWriter.num(h.decMbs(), 3),
                        ResultWriter.num(l.encMbs(), 3), ResultWriter.num(l.decMbs(), 3),
                        ResultWriter.num(d.encMbs(), 3), ResultWriter.num(d.decMbs(), 3));
                boolean lzwBetter = l.bpb() < h.bpb();
                String huffCell = ResultWriter.num(h.bpb(), 2);
                String lzwCell = ResultWriter.num(l.bpb(), 2);
                table.line("\\texttt{" + ResultWriter.latex(name) + "} & " + Math.round(data.length / 1024.0)
                        + " & " + ResultWriter.num(h0, 2)
                        + " & " + (lzwBetter ? huffCell : "\\textbf{" + huffCell + "}")
                        + " & " + (lzwBetter ? "\\textbf{" + lzwCell + "}" : lzwCell)
                        + " & " + ResultWriter.num(d.bpb(), 2)
                        + " & " + ResultWriter.num(h.encMbs(), 0)
                        + " & " + ResultWriter.num(l.encMbs(), 0) + " \\\\");
                totalBytes += data.length;
                huffBytes += h.encodedBytes();
                lzwBytes += l.encodedBytes();
                deflateBytes += d.encodedBytes();
                files++;
                if (lzwBetter) {
                    lzwWins++;
                }
            }
        }
        csv.write();
        table.write();
        macros.put("resCorpusFiles", Integer.toString(files));
        macros.put("resLzwWins", Integer.toString(lzwWins));
        if (totalBytes > 0) {
            macros.put("resCorpusHuffBpb", ResultWriter.num(8.0 * huffBytes / totalBytes, 2));
            macros.put("resCorpusLzwBpb", ResultWriter.num(8.0 * lzwBytes / totalBytes, 2));
            macros.put("resCorpusDeflateBpb", ResultWriter.num(8.0 * deflateBytes / totalBytes, 2));
        }
    }

    // ---------------------------------------------------------------- E4 LZW dictionary size

    private void lzwBits() throws IOException {
        Map<String, Path> candidates = new LinkedHashMap<>();
        candidates.put("dickens", corpusDir.resolve("silesia").resolve("dickens"));
        candidates.put("xml", corpusDir.resolve("silesia").resolve("xml"));
        candidates.put("ecoli", corpusDir.resolve("canterbury").resolve("E.coli"));
        Map<String, byte[]> inputs = new LinkedHashMap<>();
        for (Map.Entry<String, Path> c : candidates.entrySet()) {
            if (Files.isRegularFile(c.getValue())) {
                inputs.put(c.getKey(), Files.readAllBytes(c.getValue()));
            } else {
                err.println("Warning: " + c.getValue() + " not found; skipping in E4");
            }
        }
        if (inputs.isEmpty()) {
            err.println("Warning: no E4 input files found; e4_lzw_bits.csv not written");
            return;
        }
        List<String> columns = new ArrayList<>(List.of("bits"));
        for (String id : inputs.keySet()) {
            columns.addAll(List.of(id + "_bpb", id + "_enc_ms", id + "_dec_ms"));
        }
        ResultWriter csv = ResultWriter.csv(outDir.resolve("e4_lzw_bits.csv"), columns.toArray(new String[0]));
        int[] bits = quick ? new int[] {9, 12, 16} : new int[] {9, 10, 11, 12, 13, 14, 15, 16};
        Map<String, Integer> bestBits = new LinkedHashMap<>();
        Map<String, Double> bestBpb = new LinkedHashMap<>();
        for (int b : bits) {
            LzwCodec lzw = new LzwCodec(b);
            List<Object> row = new ArrayList<>(List.of(b));
            for (Map.Entry<String, byte[]> input : inputs.entrySet()) {
                Measurement m = measure(lzw, input.getValue());
                progress("E4 " + input.getKey() + " bits=" + b, "lzw", m);
                row.addAll(List.of(ResultWriter.num(m.bpb(), 4), ms(m.enc().median()), ms(m.dec().median())));
                if (m.bpb() < bestBpb.getOrDefault(input.getKey(), Double.MAX_VALUE)) {
                    bestBpb.put(input.getKey(), m.bpb());
                    bestBits.put(input.getKey(), b);
                }
            }
            csv.row(row.toArray());
        }
        csv.write();
        bestBits.forEach((id, b) -> macros.put("resLzwBitsBest" + capitalize(id), Integer.toString(b)));
    }

    // ---------------------------------------------------------------- environment

    private void environment() throws IOException {
        String jvm = System.getProperty("java.vm.name") + " " + System.getProperty("java.version");
        String cpu = cpuModel();
        int cores = Runtime.getRuntime().availableProcessors();
        long heapMiB = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        String date = LocalDate.now().toString();
        macros.put("resJvm", ResultWriter.latex(jvm));
        macros.put("resCpu", ResultWriter.latex(cpu));
        macros.put("resCores", Integer.toString(cores));
        macros.put("resHeap", Long.toString(heapMiB));
        macros.put("resDate", date);
        String policy = quick ? "quick: W=1, R=3 for every size"
                : "n <= 1 MiB: W=5, R=21; n <= 16 MiB: W=3, R=11; larger: W=1, R=7";
        ResultWriter.text(outDir.resolve("environment.txt"))
                .line("jvm: " + jvm)
                .line("os: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                        + " (" + System.getProperty("os.arch") + ")")
                .line("cpu: " + cpu)
                .line("cores: " + cores)
                .line("max_heap_mib: " + heapMiB)
                .line("date: " + date)
                .line("mode: " + (quick ? "quick" : "full"))
                .line("warmup_repetitions: " + policy)
                .line("statistic: median of R timed iterations; ci = 95% CI half-width of the mean (Student t)")
                .line("timer: System.nanoTime, encode and decode timed separately; System.gc() before each configuration")
                .line("global_warmup: " + (quick ? "64 KiB x 1" : "4 MiB x 3") + " iterations per codec and synthetic source")
                .line("seed: " + SEED)
                .line("e1_sizes: 2^" + minExponent() + "..2^" + (quick ? 14 : 24)
                        + " (dickens prefixes up to 2^" + (quick ? 14 : 23) + ")")
                .line("e1_fit_min_n: " + (quick ? "all points" : "2^16"))
                .line("e2_n: 2^" + (quick ? 14 : 23) + ", k in " + Arrays.toString(E2_ALPHABETS))
                .line("e3_codecs: Huffman, LZW(16), DEFLATE level 6 (reference)")
                .line("e4_max_bits: " + (quick ? "9,12,16" : "9..16"))
                .line("corpus: " + corpusDir.toAbsolutePath())
                .write();
    }

    private static String cpuModel() {
        try {
            for (String line : Files.readAllLines(Paths.get("/proc/cpuinfo"), StandardCharsets.UTF_8)) {
                if (line.startsWith("model name")) {
                    int colon = line.indexOf(':');
                    return line.substring(colon + 1).trim().replaceAll("\\s+", " ");
                }
            }
        } catch (IOException | RuntimeException e) {
            // Not Linux or unreadable: fall back to the architecture name.
        }
        return System.getProperty("os.arch");
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
