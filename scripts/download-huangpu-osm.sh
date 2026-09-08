#!/usr/bin/env bash
# 下载上海黄浦 / 外滩 / 人民广场一带 OSM 路网（ODbL，© OpenStreetMap contributors）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/ssd-dispatch/src/main/resources/osm/shanghai-huangpu.osm"
mkdir -p "$(dirname "$OUT")"
echo "下载 OSM → $OUT"
curl -sS --fail --max-time 90 -G "https://overpass-api.de/api/interpreter" \
  --data-urlencode 'data=[out:xml][timeout:80];(way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|pedestrian|service)$"](31.220,121.460,31.245,121.500););(._;>;);out body;' \
  -o "$OUT"
ls -lh "$OUT"
echo "节点 $(grep -c '<node ' "$OUT" || true)  道路 $(grep -c '<way ' "$OUT" || true)"
echo "完成。随后 docker compose up -d neo4j 并启动 ssd-dispatch（启动时 UNWIND+MERGE 导入），或运行 scripts/import-neo4j.sh"
