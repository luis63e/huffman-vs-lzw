# Huffman vs. LZW

[![CI](https://github.com/luis63e/huffman-vs-lzw/actions/workflows/ci.yml/badge.svg)](https://github.com/luis63e/huffman-vs-lzw/actions/workflows/ci.yml)

Theoretical and experimental comparison of two lossless compression algorithms, **static
canonical Huffman coding** and **LZW (Lempel-Ziv-Welch)**, for the research project of the
Algorithms course at Universidad de Lima (2026-2).

## Contents

| Folder | Contents |
|---|---|
| [`code/`](code/) | NetBeans *Java with Ant* project: both codecs, 43 JUnit tests, CLI and benchmark harness ([README](code/README.md)) |
| [`data/`](data/) | Script that downloads the Canterbury and Silesia corpora with pinned SHA-256 checksums |
| [`results/`](results/) | Raw benchmark output (CSV) used by the report, plus an independent replica of the scaling experiment |
| [`report/`](report/) | LaTeX source of the technical report ([README](report/README.md)) |
| [`entrega/`](entrega/) | Compiled report (PDF) |

## Quick start

```bash
source env.sh                 # portable JDK, Ant and Tectonic in .tools/ (see below)
data/download-corpora.sh      # Canterbury + Silesia corpora into data/corpus/
cd code && ant test           # run the JUnit suite
ant benchmark                 # run experiments E1-E4 into ../results
cd ../report && ./build.sh    # compile report/build/main.pdf
```

`env.sh` expects a JDK in `.tools/jdk`, Apache Ant in `.tools/ant` and Tectonic in
`.tools/tectonic`. Any JDK 17+ and Ant 1.10+ on the `PATH` work as well; the project also opens
directly in NetBeans (`File > Open Project`, select `code/`).

## Main findings

- Encoding and decoding of both algorithms scale linearly with input size (log-log slopes close
  to 1), as predicted by their complexity analysis.
- Huffman decodes more slowly than it encodes; LZW shows the opposite asymmetry.
- LZW produces smaller output on 24 of the 26 corpus files, but expands incompressible data.
- DEFLATE, which combines LZ77 and Huffman, beats both, showing that the two ideas are
  complementary.
