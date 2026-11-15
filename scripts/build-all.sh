#!/bin/sh
# Builds a TrimPerks jar for every supported Minecraft version, plus the shared resource pack, into build/release/.
# One jar per version family: 1.21.10 covers 1.21.9-1.21.10, 26.1 covers 26.1-26.1.2.
set -e
cd "$(dirname "$0")/.."
rm -rf build/release
mkdir -p build/release
for mc in 1.21.10 1.21.11 26.1 26.2 26.3; do
	echo "== Minecraft $mc"
	rm -f build/libs/trimperks-*.jar
	./gradlew build -Pmc="$mc" --console=plain -q
	cp build/libs/trimperks-*+"$mc".jar build/release/
done
cp build/TrimPerks-Resources.zip build/release/
ls -l build/release
