package com.shansuda.dispatch.neo4j;

/** @deprecated 实现已迁到 ssd-common，保留子类以免旧 import 断裂。 */
@Deprecated
public class Neo4jRoadStore extends com.shansuda.common.route.Neo4jRoadStore {
    public Neo4jRoadStore(String uri, String user, String password) {
        super(uri, user, password);
    }
}
