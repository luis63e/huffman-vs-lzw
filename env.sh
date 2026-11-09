# Source this file to use the portable toolchain: `source env.sh`
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
export JAVA_HOME="$ROOT/.tools/jdk"
export ANT_HOME="$ROOT/.tools/ant"
export PATH="$JAVA_HOME/bin:$ANT_HOME/bin:$ROOT/.tools/tectonic:$PATH"
