#!/usr/bin/env bash
# Downloads the standard lossless-compression corpora used in the experiments:
#   Canterbury corpus + large corpus (Arnold & Bell, 1997) and Silesia corpus (Deorowicz, 2003).
# Files are plain data; they are only read as bytes by the benchmark.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p corpus/canterbury corpus/silesia downloads
fetch() { [ -f "downloads/$2" ] || curl -fL --retry 3 -o "downloads/$2" "$1"; }
fetch https://corpus.canterbury.ac.nz/resources/cantrbry.tar.gz cantrbry.tar.gz
fetch https://corpus.canterbury.ac.nz/resources/large.tar.gz large.tar.gz
fetch https://sun.aei.polsl.pl/~sdeor/corpus/silesia.zip silesia.zip
sha256sum -c checksums.sha256
tar -xzf downloads/cantrbry.tar.gz -C corpus/canterbury --no-same-owner
tar -xzf downloads/large.tar.gz -C corpus/canterbury --no-same-owner
unzip -oq downloads/silesia.zip -d corpus/silesia
echo "Corpora ready in $(pwd)/corpus"
