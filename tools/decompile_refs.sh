#!/usr/bin/env bash
# Decompiles the reference mods from the Groid Pack instance for study (never commit the output).
#   tools/decompile_refs.sh [out_dir]      default: /tmp/papon_refs
# Needs java 17+. Downloads Vineflower from Maven Central on first run.
set -euo pipefail
OUT="${1:-/tmp/papon_refs}"
MODS="$HOME/Documents/curseforge/minecraft/Instances/Groid Pack OG/mods"
VF="$OUT/vineflower.jar"
mkdir -p "$OUT"
[ -f "$VF" ] || curl -sL -o "$VF" https://repo1.maven.org/maven2/org/vineflower/vineflower/1.10.1/vineflower-1.10.1.jar

decomp() {  # name jar package-path...
  local name="$1" jar="$2"; shift 2
  mkdir -p "$OUT/$name/jar" "$OUT/$name/src"
  (cd "$OUT/$name/jar" && unzip -o -q "$MODS/$jar")
  for p in "$@"; do
    java -jar "$VF" -log=ERROR "$OUT/$name/jar/$p" "$OUT/$name/src/$(basename "$p")" >/dev/null 2>&1 || true
  done
  echo "$name -> $OUT/$name (assets in jar/, sources in src/)"
}

decomp walking_crops "walking crops-1.0.7.jar" net/mcreator/wkcr/entity net/mcreator/wkcr/client
decomp cataclysm "L_Enders_Cataclysm-3.31.jar" \
  com/github/L_Ender/cataclysm/entity/InternalAnimationMonster \
  com/github/L_Ender/cataclysm/entity/effect \
  com/github/L_Ender/cataclysm/client/animation
decomp block_factorys_bosses "block_factorys_bosses-2.1.2-forge-1.20.1.jar" \
  net/unusual/block_factorys_bosses/client/camera \
  net/unusual/block_factorys_bosses/entity/boss \
  net/unusual/block_factorys_bosses/state
# Dungeon Now Loading: the Fairkeeper serpents' models (tools/gen_serpents.py reads their geometry)
decomp dnl "Dungeon Now Loading-forge-1.20.1-2.2.jar" dev/hexnowloading/dungeonnowloading/entity/client
