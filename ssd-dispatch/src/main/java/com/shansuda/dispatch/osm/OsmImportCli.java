package com.shansuda.dispatch.osm;

import com.shansuda.common.route.GridPathFinder;
import com.shansuda.common.route.OsmGraphLoader;
import com.shansuda.common.route.Neo4jRoadStore;

/**
 * 独立导入：docker compose up -d neo4j 后执行
 * {@code cd ssd-dispatch && mvn -q exec:java -Dexec.mainClass=com.shansuda.dispatch.osm.OsmImportCli}
 */
public final class OsmImportCli {

    private OsmImportCli() {
    }

    public static void main(String[] args) {
        String uri = args.length > 0 ? args[0] : "bolt://127.0.0.1:7687";
        String user = args.length > 1 ? args[1] : "neo4j";
        String password = args.length > 2 ? args[2] : "shansuda";
        GridPathFinder graph = OsmGraphLoader.load();
        System.out.println("路网节点 " + graph.nodes().size() + " 边 " + graph.edgeCount());
        Neo4jRoadStore store = new Neo4jRoadStore(uri, user, password);
        try {
            if (!store.ping()) {
                System.err.println("Neo4j 不可用: " + uri);
                System.exit(1);
            }
            store.importGrid(graph);
            System.out.println("已 UNWIND+MERGE 导入 version=" + Neo4jRoadStore.VERSION);
        } finally {
            store.close();
        }
    }
}
