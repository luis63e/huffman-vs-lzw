#!/usr/bin/env bash
# Builds report/build/main.pdf from the LaTeX sources and the benchmark results.
# Tectonic runs BibTeX and the required reruns automatically.
set -euo pipefail
cd "$(dirname "$0")"
source ../env.sh

# The report reads results/<file>; copy a fresh snapshot of ../results.
rm -rf results && mkdir results && cp ../results/*.csv ../results/*.tex results/
# Fits of the independent E1 replica (same protocol), shown next to the main fits.
if [[ -f ../results/replica/fits.csv ]]; then
  cp ../results/replica/fits.csv results/fits_replica.csv
fi

mkdir -p build
tectonic -X compile main.tex --keep-logs --outdir build
echo "PDF: $(pwd)/build/main.pdf"
