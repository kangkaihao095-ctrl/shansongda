# 闪送达

同城即时配送微服务系统：用户下单、商家接单、骑手抢单 / 自动派单、秒杀抢券、路径规划与履约流转。

后端为 Spring Boot 多模块，前端为 Vue 3。网关统一入口，本地可通过 Docker Compose 启动中间件后跑通三端页面。

## 功能

- **用户端**：店铺推荐与分类、购物车结算、优惠券 / 会员、秒杀与抢券、下单支付、配送轨迹、评价与打赏
- **骑手端**：上线 / 接单状态、抢单大厅、到店 / 配送 / 完成、工时统计、骑手资料
- **商家端**：接单 / 拒单、商品上下架与改价、自动接单开关、经营统计与 CSV 报表
- **运力调度**：`骑手 → 商家 → 用户` 两段最短路；有 Neo4j 时走 GDS A\*（缺坐标时 Dijkstra）；无 Neo4j 时使用 classpath 中的 OSM 内存图，算法同构
- **高并发活动**：Caffeine 本地缓存 + Redis Lua 原子扣库存与幂等 + Redisson 分布式锁 + RabbitMQ 异步落单
- **订单分片**：ShardingSphere-JDBC，订单 ID hash，`4` 库 × `8` 表（本地为同一 MySQL 上的 `ssd_order_0..3`）
- **分布式 ID**：Leaf 号段模式，号段表 `ssd_leaf.leaf_alloc`
- **骑手 LBS**：Canal 监听 `ssd_account.rider` Binlog 同步 Elasticsearch；无 ES 时由 account 对 MySQL 坐标做 Haversine 过滤与排序
- **履约**：订单状态机约束合法迁移；运费、违约金使用策略模式

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 语言 / 运行时 | Java 17、Node.js 18+ |
| 后端 | Spring Boot 3.3.5、Spring Cloud Gateway 2023.0.3、Spring Data JPA |
| 数据 | MySQL 8.0、Redis 7、RabbitMQ 3.13、Neo4j 5.18（GDS 插件）、Elasticsearch 8.13.4、Canal 1.1.8 |
| 中间件客户端 | ShardingSphere-JDBC 5.5.1、Redisson 3.37.0、Caffeine、JJWT 0.12.6 |
| 前端 | Vue 3、Vue Router、Vite 6 |
| 地图展示 | 有 `SSD_AMAP_KEY` 时用高德 JS API；无 Key 时用高德公开栅格瓦片，OSM 瓦片作失败兜底。路径计算不走高德 |

## 架构

```
浏览器 :5175
    │  /api 代理
    ▼
ssd-gateway :18080
    ├─ ssd-account  :18081   账号 / 商家 / 券 / 会员 / 附近骑手
    ├─ ssd-activity :18082   抢券 / 秒杀 / 抢单 Lua
    ├─ ssd-order    :18083   订单 / 支付 / 履约 / 本机路径规划
    ├─ ssd-dispatch :18084   可选，Neo4j GDS 路径规划
    └─ ssd-search   :18085   可选，Canal → Elasticsearch
```

默认 `SSD_MODE=auto`：

- Redis 可达时活动库存走 Lua；`dry-run` 时走内存实现
- Neo4j 可达且启动了 `ssd-dispatch` 时导入 OSM 并走 GDS；否则 `ssd-order` 内嵌同一份 OSM 内存图
- Elasticsearch / Canal 可达且启动了 `ssd-search` 时同步骑手索引；否则 `GET /api/riders/nearby` 读 MySQL

网关对外路径前缀为 `/api`。除 `/api/auth/**`、`/api/map/**`、`/api/avatars/**`、评价配图 GET、`/actuator/**` 外，请求需带 `Authorization: Bearer <jwt>`。

## 仓库结构

```
shansongda/
├── docker-compose.yml          中间件
├── docker/mysql/init.sql       库表与分片建表
├── docker/canal/               Canal 实例配置
├── pom.xml                     Maven 父工程
├── ssd-common/                 公共 JWT、路径规划、目录图
├── ssd-gateway/                Spring Cloud Gateway
├── ssd-account/
├── ssd-activity/
├── ssd-order/                  含 shardingsphere.yaml、Leaf
├── ssd-dispatch/               Neo4j 导入与 GDS 规划
├── ssd-search/                 Canal 消费与 ES 索引
├── frontend/                   Vue 3
└── scripts/                    OSM 下载与 Neo4j 导入
```

## 环境要求

- JDK 17
- Maven 3.8+
- Node.js 18+
- Docker / Docker Compose（用于中间件）

本机端口需空闲：`3316`（MySQL）、`6389`（Redis）、`5682` / `15682`（RabbitMQ）、`5175`（前端）、`18080`–`18083`（网关与三个必选服务）。启用 Neo4j / ES 时还需 `7474`、`7687`、`9210`、`11111`、`18084`、`18085`。

Elasticsearch 在 Linux 上可能需要：

```bash
sudo sysctl -w vm.max_map_count=262144
```

## 快速开始

以下为最小可运行组合：MySQL + Redis + RabbitMQ，以及 gateway / account / activity / order / 前端。路径规划与附近骑手走本机兜底实现。

### 1. 启动中间件

```bash
docker compose up -d mysql redis rabbitmq
```

等待 MySQL healthy。首次启动会执行 `docker/mysql/init.sql`，创建 `ssd_account`、`ssd_activity`、`ssd_leaf`、`ssd_order_0..3` 及 `4×8` 订单分片表。

账号密码（与 Compose / 应用配置一致）：

| 组件 | 地址 | 账号 | 密码 |
| --- | --- | --- | --- |
| MySQL | `127.0.0.1:3316` | `shansuda` | `shansuda` |
| Redis | `127.0.0.1:6389` | — | — |
| RabbitMQ AMQP | `127.0.0.1:5682` | `shansuda` | `shansuda` |
| RabbitMQ 管理台 | `http://127.0.0.1:15682` | `shansuda` | `shansuda` |

### 2. 启动后端

在仓库根目录分别开四个终端（或后台进程）：

```bash
mvn -pl ssd-account -am spring-boot:run
mvn -pl ssd-activity -am spring-boot:run
mvn -pl ssd-order -am spring-boot:run
mvn -pl ssd-gateway -am spring-boot:run
```

`ssd-account` 首次启动会幂等写入目录种子（8 品类 × 至少 15 家店、每店 42 个 SKU），耗时较长，日志出现启动完成后即可访问。种子失败只打日志，不中断进程。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 [http://127.0.0.1:5175](http://127.0.0.1:5175)。Vite 将 `/api` 代理到 `http://127.0.0.1:18080`。

### 4. 演示账号

登录页可切换用户 / 骑手 / 商家。密码均为 `demo123456`。

| 角色 | 手机号 | 说明 |
| --- | --- | --- |
| USER | `13800000001` | 用户端首页、下单、券与会员 |
| RIDER | `13800000002` | 骑手大厅、抢单与配送 |
| MERCHANT | `13800000003` | 商家接单与商品管理 |

模拟支付、开通会员的收银密码为 `147258`（不真实扣款）。

## 启用 Neo4j 与 Elasticsearch（可选）

完整中间件：

```bash
docker compose up -d
```

包含 Neo4j（`neo4j/shansuda`，已启用 GDS 插件）、Elasticsearch 单节点（安全关闭）、Canal。内存占用明显高于最小组合。

路网数据为上海黄浦 / 外滩 / 人民广场一带 OSM 摘录（`ssd-dispatch/src/main/resources/osm/`，许可 ODbL）。`ssd-dispatch` 启动时对图执行 `UNWIND + MERGE` 幂等导入；也可单独执行：

```bash
./scripts/import-neo4j.sh
```

然后启动可选服务：

```bash
mvn -pl ssd-dispatch -am spring-boot:run
mvn -pl ssd-search -am spring-boot:run
```

当前网关把 `/api/dispatch/**` 与 `/api/riders/nearby` 分别打到 **order:18083** 与 **account:18081**。要让流量打到 `ssd-dispatch` / `ssd-search`，需改 `ssd-gateway` 的路由目标端口为 `18084` / `18085`。

重新下载 OSM：

```bash
./scripts/download-huangpu-osm.sh
```

## 配置

环境变量（均可不设，使用仓库默认值）：

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `SSD_JWT_SECRET` | `change-me-in-prod-please-32chars` | JWT 密钥，各服务须一致，长度 ≥ 32 |
| `SSD_MODE` | `auto` | `auto` / `live` / `dry-run` |
| `SSD_AMAP_KEY` | 空 | 有则前端走高德 JS API |
| `SSD_RIDER_MAX_WORK_HOURS` | `8` | 骑手当日在线工时上限（小时） |
| `SSD_SECKILL_ROTATE_MINUTES` | `10` | 秒杀展示 SKU 轮换间隔 |
| `SSD_RECOMMEND_MAX_KM` | `5` | 推荐过滤与结算超距半径（千米） |

端口一览：

| 进程 | 端口 |
| --- | --- |
| 前端 Vite | 5175 |
| ssd-gateway | 18080 |
| ssd-account | 18081 |
| ssd-activity | 18082 |
| ssd-order | 18083 |
| ssd-dispatch | 18084 |
| ssd-search | 18085 |

## 订单状态

```
CREATED → MERCHANT_PENDING → PAID → ACCEPTED → ARRIVED → DELIVERING → COMPLETED
CREATED → CANCELLING → CANCELLED
MERCHANT_PENDING / PAID / ACCEPTED / ARRIVED / DELIVERING / COMPLETED → REFUNDING → REFUNDED
REFUNDING → REFUND_REJECTED（恢复 resume_status）
```

- 支付成功：`CREATED → MERCHANT_PENDING`；商家接单后进入 `PAID`（待骑手接单）
- 抢单仅允许 `PAID → ACCEPTED`
- `DELIVERING` 不可直接取消
- `COMPLETED` / `CANCELLED` / `REFUNDED` 为终态

运费策略：`DistanceFreight`（商家→用户直线距离分档）、`PeakFreight`（高峰在距离运费上乘系数）。违约策略按当前状态计 `penalty_cents`；配送中取消返回 409。

## 活动链路

活动类型：`COUPON`（抢券）、`SECKILL`（秒杀）、`GRAB`（抢单）。

Lua 一次 `EVAL`：校验幂等键 → 读库存 → 判断 → 扣减 → 写幂等键与 TTL。返回码：`1` 成功、`0` 库存不足、`-1` 重复（回放首次结果）。幂等键由服务端按业务维度拼装，库存数据不进入 Caffeine。

RabbitMQ Exchange：`ssd.activity`（`seckill.success` / `coupon.success` / `grab.success`）、`ssd.order`（`order.paid`）。

## 测试

```bash
mvn test
cd frontend && npm test
```

后端覆盖库存并发与幂等、状态机非法迁移、Leaf 号段、分片路由、A\* / Dijkstra、ES 旧 version 不覆盖等。前端为 Vitest（API 客户端、路由守卫、状态文案、购物车、秒杀轮询、登录页）。

## HTTP 接口（节选）

基址：`http://127.0.0.1:18080`（或经前端 `/api`）。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/register` | 注册 `{phone, password, role}` |
| POST | `/api/auth/login` | 登录 |
| GET | `/api/merchants/recommend` | 首页推荐 |
| POST | `/api/orders/preview` | 结算预览 |
| POST | `/api/orders` | 下单 |
| POST | `/api/orders/{id}/pay` | 模拟支付 `{channel, password}` |
| POST | `/api/activities/{id}/seckill` | 秒杀 |
| POST | `/api/activities/{id}/grab-coupon` | 抢券 |
| POST | `/api/orders/{orderId}/grab` | 骑手抢单 |
| POST | `/api/dispatch/route` | 两段路径规划 |
| GET | `/api/riders/nearby` | 附近骑手 |

完整接口与字段见 `DEV_TASK.md`。

## 数据与许可

- 业务种子与演示账号由 `ssd-account` / `ssd-order` 启动时写入，密码哈希为 BCrypt
- 路网摘录来自 [OpenStreetMap](https://www.openstreetmap.org/copyright)，许可 [ODbL](https://opendatacommons.org/licenses/odbl/)，© OpenStreetMap contributors
- 店铺与菜品图使用仓库内本地资源，不依赖外部随机图床
