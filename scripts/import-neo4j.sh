#!/usr/bin/env bash
# 起 Neo4j 并把 classpath 中的黄浦 OSM 导入 Intersection / ROAD（UNWIND+MERGE 幂等）
set -euo pipefail
export JAVA_HOME="${JAVA_HOME:-/usr/local/opt/openjdk@17}"
export PATH="$JAVA_HOME/bin:/usr/local/bin:$PATH"
export DOCKER_HOST="${DOCKER_HOST:-unix:///Users/lumengkang/.colima/default/docker.sock}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
echo "启动 neo4j（Colima ~4GB 时不要同时起 elasticsearch / canal）"
docker compose up -d neo4j
echo "等待 Bolt 7687..."
for i in $(seq 1 40); do
  if nc -z 127.0.0.1 7687 2>/dev/null; then
    break
  fi
  sleep 2
done
if ! nc -z 127.0.0.1 7687 2>/dev/null; then
  echo "Neo4j 尚未监听 7687，请稍后重试或查看 docker logs ssd-neo4j"
  exit 1
fi
cd "$ROOT/ssd-dispatch"
mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.shansuda.dispatch.osm.OsmImportCli
echo "导入完成。路径规划权威仍是 Neo4j GDS A*；无 Neo4j 时 dispatch 用同一片 OSM 内存图。"
