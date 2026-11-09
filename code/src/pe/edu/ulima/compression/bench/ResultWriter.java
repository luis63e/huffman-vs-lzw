package pe.edu.ulima.compression.bench;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Accumulates the lines of one result file and writes it in UTF-8. CSV output is pgfplots
 * friendly: comma separated, one header row, no quoting, numbers formatted with {@link Locale#ROOT}.
 */
final class ResultWriter {

    private final Path path;
    private final List<String> lines = new ArrayList<>();

    private ResultWriter(Path path) {
        this.path = path;
    }

    static ResultWriter csv(Path path, String... columns) {
        ResultWriter writer = new ResultWriter(path);
        writer.lines.add(String.join(",", columns));
        return writer;
    }

    static ResultWriter text(Path path) {
        return new ResultWriter(path);
    }

    /** Appends one CSV row; each value is written with {@link String#valueOf(Object)}. */
    ResultWriter row(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values[i]);
        }
        lines.add(sb.toString());
        return this;
    }

    ResultWriter line(String line) {
        lines.add(line);
        return this;
    }

    void write() throws IOException {
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    /** Fixed-point number with {@code digits} decimals, independent of the default locale. */
    static String num(double value, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f", value);
    }

    /** Escapes the characters that are special in LaTeX text mode. */
    static String latex(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\':
                    sb.append("\\textbackslash{}");
                    break;
                case '~':
                    sb.append("\\textasciitilde{}");
                    break;
                case '^':
                    sb.append("\\textasciicircum{}");
                    break;
                case '&': case '%': case '$': case '#': case '_': case '{': case '}':
                    sb.append('\\').append(c);
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }
}
