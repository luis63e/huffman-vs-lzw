# HuffmanVsLZW

Java implementations of **static canonical Huffman coding** and **LZW (Lempel-Ziv-Welch)**,
plus the experiment harness used in the report *Huffman vs. LZW: an experimental comparison of
lossless compression algorithms* (Universidad de Lima, 2026-2).

## Opening in NetBeans

`File > Open Project` and select this `code/` folder. It is a standard *Java with Ant*
project (`nbproject/`), targeting Java 17 or newer. JUnit 4.13.2 and Hamcrest 1.3 are bundled
in `lib/`, so no library setup is needed.

- **Run** (F6) runs `compression.app.Main` without arguments, which prints a
  worked example of both algorithms and the command-line usage.
- **Test** (Alt+F6) runs the JUnit suite.

## Command line

```bash
ant jar          # builds dist/HuffmanVsLZW.jar
ant test         # runs the JUnit tests
ant benchmark    # runs every experiment and writes ../results (needs ../data/corpus)

java -jar dist/HuffmanVsLZW.jar compress huffman input.txt input.huf
java -jar dist/HuffmanVsLZW.jar compress lzw input.txt input.lzw     # or lzw-9 .. lzw-16
java -jar dist/HuffmanVsLZW.jar decompress input.lzw restored.txt    # format auto-detected
java -jar dist/HuffmanVsLZW.jar stats input.txt                      # entropy and both ratios
java -jar dist/HuffmanVsLZW.jar demo
```

Exit codes: `0` success, `1` runtime error (unreadable file, corrupt archive), `2` usage error.

Benchmark options are passed through `-Dbench.args`, for example
`ant benchmark -Dbench.args="--quick --only e1,e2"`. Available experiments: `e1` scaling with
input size, `e2` entropy sweep, `e3` Canterbury and Silesia corpora, `e4` LZW dictionary size.

## Corpora

`../data/download-corpora.sh` downloads the Canterbury corpus (Arnold & Bell, 1997) and the
Silesia corpus (Deorowicz, 2003), verifies their SHA-256 checksums and extracts them into
`../data/corpus/`.

## Source layout

| Package | Contents |
|---|---|
| `compression` | `Codec` interface shared by every algorithm |
| `...io` | `BitWriter` / `BitReader`: MSB-first variable-width bit I/O |
| `...huffman` | `HuffmanCodec`: heap-based code lengths, canonical codes, canonical decoding |
| `...lzw` | `LzwCodec`: 9-16 bit variable-width codes, hash-table dictionary, CLEAR reset |
| `...bench` | `BenchmarkRunner`, `Stats`, `DataGenerator`, `DeflateCodec` (reference only) |
| `...app` | `Main`: command-line interface |
