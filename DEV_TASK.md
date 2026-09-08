# 闪送达开发任务书

对照 [md/闪速达_后端项目.md](../md/闪速达_后端项目.md) 的面试口径。原 MD 按单体叙述，本工程按微服务落地。**编码只对照本文件，不现场发明字段。**

## 0. 范围与默认决策

做：

- 四个本机常驻 Spring Boot 进程（account / activity / order / gateway）+ 最小 Vue 3。`ssd-dispatch` / `ssd-search` **代码保留、本机不启动**（Colima 内存不够）。
- 五条主链：调度 / 高并发活动 / 分库分表订单 / Canal+ES LBS / 履约状态机+策略。
- 支付：`POST /api/orders/{id}/mock-pay` 模拟支付。
- 活动三种：`COUPON` 抢券、`SECKILL` 秒杀、`GRAB` 抢单。
- 派单默认 **骑手抢单**；另提供 `POST /api/dispatch/assign/{orderId}` 按路径代价自动指派（走 account 附近骑手 + order 内嵌规划）。

不做：

- Nacos / 微信支付宝进件 / 真实 4 台 MySQL / 在线扩容迁移 / 把压测数字当线上 SLA。
- 独立 Leaf 集群；号段模式内嵌在 order-service。
- 履约单独进程。

本地模式：`ssd.mode=auto`。本机不跑 Neo4j / ES / Canal / 18084 / 18085。算路并进 **order:18083**（`GridPathFinder` 内存网格 A*，无启发式则 Dijkstra，同构路网不是高德）；附近骑手并进 **account:18081**（MySQL `rider` + Haversine）。生产口径仍是 Neo4j GDS 与 Canal+ES。无 Canal 时 search 单测用假事件，compose 演示必须起 Canal。

## 1. 订单状态机

```
CREATED → MERCHANT_PENDING → PAID → ACCEPTED → ARRIVED → DELIVERING → COMPLETED
CREATED → CANCELLING → CANCELLED
MERCHANT_PENDING / PAID / ACCEPTED / ARRIVED / DELIVERING / COMPLETED → REFUNDING → REFUNDED
REFUNDING → REFUND_REJECTED（恢复 resume_status 继续履约）
```

对外中文：待支付 / 待商家接单 / 商家已接单·备餐中 / 待骑手接单 / 骑手已接单 / 骑手到店 / 配送中（预计 HH:mm 送达）/ 已送达 / 已完成 / 取消中 / 已取消 / 退款中 / 退款成功 / 退款拒绝。

用户端 `PAID` 展示「商家已接单·备餐中」，骑手/商家端同一状态展示「待骑手接单」。`COMPLETED` 用户/商家「已完成」，骑手「已送达」。无 ETA 时写「商家备餐中」（待接单/备餐）或「骑手正在赶往商家」，禁止「规划中」。三端文案以 `frontend/src/status.js` 为准。

- 支付成功：`CREATED → MERCHANT_PENDING`（待商家接单），商家接单后才到 `PAID`（待骑手接单）。抢单仍仅 `PAID → ACCEPTED`。
- 未支付取消必须填原因，先入 `CANCELLING` 再确认 `CANCELLED`，不是点一下立刻消失。
- 已支付走退款申请 `REFUNDING`，商家同意/拒绝；配送中不能瞬删，申请退款按现有 `PenaltyStrategy` 记 `penalty_cents`。
- `DELIVERING` **不能**直接取消（取消受状态约束）。
- `COMPLETED` / `CANCELLED` / `REFUNDED` 为终态，禁止回迁。
- 状态机集中在 `com.shansuda.order.fulfill.OrderStateMachine`，Service 不得散落 if-else 迁状态。

## 2. 策略模式

- 状态机：流程能不能变。
- 策略：当前场景用哪条规则。二者不互相替代。

运费 `FreightStrategy`（下单时选一种，写入订单快照，事后不因策略代码变更改历史单）：

- `DistanceFreight`：按商家→用户直线距离分档。
- `PeakFreight`：高峰时段在距离运费上乘系数。

违约 `PenaltyStrategy`（取消时按**当前状态**）：

- `CREATED`：0。
- `PAID`：固定小额（任务书冻结 `200` 分）。
- `ACCEPTED`：运费快照金额（补偿骑手侧记账，本项目只写 `penalty_cents`）。
- `DELIVERING`：拒绝取消，返回 409。

## 3. 核心表与分片

所有订单相关表按 **订单 ID** 分片：`db = orderId % 4`，`table = (orderId / 4) % 8`，共 32 分片。本地 1 个 MySQL、4 个 schema `ssd_order_0..3`，每库 `t_order_0..7`。中间件：ShardingSphere-JDBC。

`ssd_leaf.leaf_alloc`

- `biz_tag` PK（订单用 `order_id`）
- `max_id`, `step`（默认 1000）, `description`, `update_time`
- 双 buffer 异步预加载；**不依赖时钟**，无时钟回拨问题。

`ssd_account`

- `app_user`：id, phone, password_hash, role(`USER|RIDER|MERCHANT`), display_name, avatar_url（可空，相对路径或 data URL）, status, created_at, last_login_at（发券活跃度）
- `rider`：user_id PK, online_status(`OFFLINE|ONLINE`), accept_status(`IDLE|BUSY`), lat, lon, update_time, version（每次位置/状态更新 +1）
- `merchant`：user_id PK, shop_name, lat, lon, address, category, cover_url, rating, promo, online_status(`ONLINE|OFFLINE`，营业中/打烊), rating_avg, rating_count, completed_count（完成单销量，供推荐）, auto_accept（自动接单，默认 false=手动）
- `merchant_sku`：id, merchant_id, name, group_name, price_cents, origin_price_cents, image_url, spec, stock, status(`ONLINE|OFFLINE`，上下架), description, detail, month_sales, like_count, sku_key；**UNIQUE(sku_key)** 供种子幂等
- `merchant_review`：id, merchant_id, order_id, user_id, score(1-5), content（可空）, photo_urls（JSON 数组，最多 3 张 `/api/review-photos/`）, rider_score(1-5 可空，仅骑手星级), sku_names, like_count, created_at；**UNIQUE(order_id)** 一单一评
- `review_like`：user_id + review_id **PRIMARY KEY**（唯一键防同一用户重复点赞同一评论；再点为取消）
- `coupon`：id, name, merchant_id（可空=平台券）, activity_id（可空）, type(`AMOUNT|PERCENT`), min_spend_cents, discount_cents, percent_off, stock, start_at, end_at, status, code（模板/实例）, member_only, icon（`minus|free|member|newcomer|category|percent`）
- `coupon_template`：code PK, name, scene(`NEWCOMER|LAPSED|FREQUENT|RETURN|MEMBER|HOME|LOGIN`), type, min_spend_cents, discount_cents, percent_off, member_only, icon, valid_seconds（新客 7 天、登录礼 24h、常规/首页 3 天；会员红包到当周周日不计此字段）
- `coupon_grant_log`：grant_key PK（`GRANT:{userId}:{date}:{scene}`、`GRANT:{userId}:ONCE:NEWCOMER`、`GRANT:{userId}:{date}:HOME:{slot}` 或 `LOGIN_GIFT:{userId}:{date}`）, user_id, scene, created_at
- `user_coupon`：id, coupon_id, user_id, status(`UNUSED|USED`)，列表对外再派生 `EXPIRED`（UNUSED 且 `end_at` 已过）, claimed_at, used_order_id；**UNIQUE(coupon_id, user_id)**。可重复发的场次券每次实例化新 coupon 行
- `member_sub`：user_id PK, plan(`MONTH|QUARTER|YEAR|AUTO_MONTH`), level(1-7), paid_cents_net（净实付分）, expire_at, status(`ACTIVE|EXPIRED`), year_member, auto_renew, updated_at
- `member_pay_log`：id, user_id, plan, channel, cents, auto_renew, created_at（开通/续期流水；连续包月假支付不真扣款也要写）
- `user_address`：id, user_id, lat, lon, detail, is_default（推荐/配送半径用**当前默认地址**）
- `rider_workday`：user_id + work_date PK, worked_seconds, forced_offline, last_tick_at。按上海时区自然日累计 `ONLINE` 时长；达到 `ssd.rider.max-work-hours`（默认 8）强制 `OFFLINE`，当日不可再上线。
- `rider_profile`：user_id PK, bio, started_on, on_time_rate, 冗余 tip_cents_total / rating_avg / rating_count / completed_count。准时率无真实超时轨迹时用种子 `on_time_rate`，简介标明口径。
- `order_tip`：order_id PK（每单最多一次）, rider_id, user_id, cents, gift_code(`WATER|MILKTEA|GIFT|CHICKEN`), created_at。对外只收礼物档，服务端映射 cents：送瓶水 200 / 请喝奶茶 500 / 送份小礼物 1000 / 加个鸡腿 2000，拒绝任意金额

`ssd_activity`

- `activity`：id, type(`COUPON|SECKILL|GRAB`), name, start_at, end_at, status
- `activity_sku`：id, activity_id, sku_id, name, price_cents, origin_stock（展示用；**真实库存只在 Redis**）
- `coupon_grant`：id, activity_id, user_id, scene, coupon_code, created_at；**UNIQUE(activity_id, user_id, scene)**
- `seckill_idem`：idem_key PK, order_id, payload JSON, created_at（DB 唯一索引兜底）

`ssd_order_x.t_order_y`

- id（Leaf）, user_id, merchant_id, rider_id（可空）, status
- goods_amount_cents, freight_cents, freight_strategy, penalty_cents
- user_lat, user_lon, merchant_lat, merchant_lon, address_detail
- activity_id（可空）, sku_snapshot JSON
- pay_channel(`WECHAT|ALIPAY`), pay_status(`UNPAID|PAID|REFUNDING|REFUNDED`), paid_at, pay_amount_cents
- cancel_reason, refund_reason, refund_reject_reason, resume_status
- idempotency_key, created_at, updated_at, version
- **UNIQUE(idempotency_key)**
- 订单号必须 `LeafAllocator.nextId()`，号段库 `ssd_leaf` 与分片业务库隔离。

深分页：禁止大 offset 的 `LIMIT offset,size`。接口使用游标：`WHERE user_id=:uid AND id < :cursor ORDER BY id DESC LIMIT :size`，联合索引 `(user_id, id)`。先查 ID 再按 ID 回表（延迟关联），列表接口不 `SELECT *` 扫全行再丢弃。

## 4. API

前缀 `/api`。除 `/auth/**`、`/actuator/health` 外需 `Authorization: Bearer <jwt>`。JWT claims：`sub=userId`, `role`, `rid=riderId?`。服务端只信 token。

账号

- `POST /auth/register` `{phone, password, role}`
- `POST /auth/login` `{phone, password}`
- `GET /me` 含 `displayName`、`avatarUrl`、`member`（等级/年费/到期）
- `GET /me/reviews?page=&size=` 我的评价：店铺名、星级、内容、配图、时间、点赞、member 徽章；点进 `/shops/:id?reviewId=`
- `GET /merchants/recommend?page=1&size=12` JWT 用户首页「为你推荐」，分页返回 `{items,page,size,total,hasNext,maxKm}`。**只用当前默认地址**算 Haversine；超过 `ssd.recommend.max-km`（默认 **5**）的店**不出现在推荐**。排序仍按 `RecommendScorer`，同分再按 merchantId 稳定切片，禁止重复同一家。前端触底加载，`hasNext=false` 展示「已经到底了」。可解释加权：有完成单历史时 `0.35*距离 + 0.25*品类亲和 + 0.20*销量 + 0.15*评分 + 0.05*在线`；未登录/无历史摊成 `0.45*距离 + 0.30*销量 + 0.20*评分 + 0.05*在线`。距离分仍 0km=1、≥8km=0（打分衰减与配送半径分开）；品类亲和=该品类在用户 COMPLETED 单中的频次/最大频次；销量=`log1p(completed_count)/log1p(max)`；评分=`rating_avg/5`；ONLINE=1，打烊=0.28。不引入 Spark
- `PUT /me` `{displayName}` 改昵称。三端「我的」不直接摊输入框；点头像或用户名弹出菜单/半屏：改昵称、换头像、取消。保存后关闭，主界面只显示名字。退出登录仍在底部。
- `POST /me/avatar` multipart 上传头像，返回 `{avatarUrl}`
- `POST /me/addresses` `{lat, lon, detail}` 仅 USER；写入 `user_address`；第一张自动默认
- `PUT /me/addresses/{id}/default` 切换默认地址；推荐立即按新坐标重算
- `PUT /me/location` `{lat, lon, detail?}` 改默认地址坐标（或无地址时新建）。首页可点顶栏地点：已有地址 / 微调 lat·lon / 上海地标（外滩、徐家汇、五角场、浦东陆家嘴）
- `GET /map/config` 公开；有 `SSD_AMAP_KEY` 则 `provider=amap` 走高德 JS API；无 Key 则 `provider=amap-tiles`，前端用高德公开栅格（`webrd0{1-4}.is.autonavi.com`）出街道路网，OSM 仅作瓦片失败兜底。地图只负责展示。路径权威是 A*/Dijkstra：有 Neo4j 时 GDS，本机无 Neo4j 进程时 **order 内嵌同构内存路网**，不是高德算路
- `PUT /riders/me/location` `{lat, lon}` 仅 RIDER；写 MySQL `rider`，version+1，并累加工时
- `PUT /riders/me/status` `{onlineStatus, acceptStatus}`；上线受当日工时上限约束
- `GET /riders/me/work-stats` 当日工时、剩余、是否强制下线、补贴口径
- `GET /riders/me/profile` 仅骑手；资料卡：昵称、简介、接单年限/送餐天数、准时率（含口径说明）、累计打赏、勋章墙
- `GET /riders/{id}/profile` 登录用户可看骑手简介摘要（订单详情 / 轨迹页）
- `PUT /merchants/me/status` `{onlineStatus: ONLINE|OFFLINE}` 仅商家；打烊后用户端该店不可下单
- `PUT /merchants/me/settings` `{autoAccept}` 仅商家；`true` 自动接单 / `false` 手动接单。支付成功进入 `MERCHANT_PENDING` 后，若自动接单则服务端立刻（`order.paid` 短延迟兜底）`merchant-accept` → `PAID`
- `GET /merchants/me/skus` 仅本店商家；商品列表含 `stock`、`soldCount`（= `month_sales`）、`priceCents`、`groupName`、`status`，并按店内分类 `groups`
- `PUT /merchants/me/skus/{id}` `{status?, priceCents?, stock?, groupName?}` 仅本店商家；改价立即对用户端店铺详情生效；上下架仍用 `ONLINE|OFFLINE`
- `GET /merchants?category=&q=&page=&size=` 分页。默认 `page=1,size=12`，`size` 上限 30。返回 `{items,page,size,total,hasNext,maxKm}`，卡片含 coverUrl / rating / ratingAvg / ratingCount / completedCount / promo / `onlineStatus` / `open` / `distanceKm` / `inRange`。超配送半径仍可出现在分类列表，但标「超配送范围不可下单」且不可结算
- `GET /merchants/{id}` 店铺详情 + 商品列表（含 sku.status / description / detail / monthSales）以及 `distanceKm` / `inRange`；超距不可加购
- `GET /merchants/{id}/skus/{skuId}` 单品详情
- `GET /merchants/{id}/reviews?page=&size=` 店铺评价：评分（星）、文字、配图、匿名昵称、时间、点赞数、菜品、**闪会员**徽章
- `POST /reviews/{id}/like` 点赞 toggle；靠 `review_like(user_id,review_id)` 唯一键防重，返回 `{likeCount,likedByMe}`
- `GET /coupons` 可领券目录（不含会员红包实例）；含 icon / iconUrl / endAt
- `GET /coupons/today` 今日按活跃度可领券包（scene / claimed / items，含券面字段）
- `GET /coupons/home-feed` 首页券区当前档：按 60s 时间片 + userId 在服务端抽模板（禁止前端假随机）。返回 `{slot,nextRefreshAt,claimed,items}`
- `POST /coupons/home-feed/claim` 领取当前档；键 `GRANT:{userId}:{date}:HOME:{slot}`
- `GET /coupons/login-gift` 当日登录礼预览（先给券面，不入账）。返回 `{eligible, alreadyClaimed, claimed, items, date, grantKey}`。已领则 `eligible=false, alreadyClaimed=true`
- `POST /coupons/login-gift` 一键领取登录礼；幂等键 `LOGIN_GIFT:{userId}:{date}`。登录接口只返回预览，不自动入账
- `POST /coupons/grant-session` 领取今日活跃度券包；幂等键 `GRANT:{userId}:{date}:{scene}`
- `POST /coupons/{id}/claim` 领取目录券
- `GET /me/coupons?status=UNUSED|EXPIRED|USED` 我的券。可逗号：`EXPIRED,USED` 为历史 Tab。UNUSED 不含过期
- `GET /member` 会员卡
- `GET /member/benefits` 本周会员红包列表（含 icon）；未开通/过期 409
- `POST /member/claim` 领取本周 3/5/8 元无门槛红包；未开通 409
- `POST /member/subscribe` `{plan: MONTH|QUARTER|YEAR|AUTO_MONTH, channel, password}` 假收银台开通，密码 `147258`。月卡 31 天 / 季卡 93 天 / 年卡 365 天并 `yearMember=true` / 连续包月 31 天并 `autoRenew=true`。连续包月到期自动续 31 天（定时任务或打开会员页时检查）；假支付不真扣款，但状态续上并写 `member_pay_log`。前端开通页四档（如月 15 / 季 40 / 年 128 / 连续包月 12 元）；年卡金色「年」标，连续包月标「连续」。计划类型写在会员卡上。徽章仍是「闪会员」+ 动效。

领券体验（组件 `CouponSheet.vue` + `CouponFace.vue` + `CouponDetail.vue`）：封面只摊面额（¥15 / 5 折）和图标，不要把券 id、到期时间写在封面上。点某张或点「查看详情」再看门槛、到期、券号、使用规则。禁止盲盒抽卡。成功后券面翻转 + 撒花，文案「已放入我的优惠券」。红底普通券 / 金底会员券。图标：满减 `minus`、无门槛 `free`、闪会员 `member`、新客 `newcomer`、品类 `category`、折扣 `percent`，资源 `/images/coupons/{icon}.svg`。「我的优惠券」卡片默认面额 + 无门槛/满减一句，点进半屏详情才看 id 和过期时间。

发券口径（美团风格，代码 `CouponGrantService` / `CouponIcons`）：

- 新用户注册 USER：自动入账 **新客 15 元无门槛**（7 天到期），键 `GRANT:{userId}:ONCE:NEWCOMER`。
- **每次登录**弹登录礼：随机 2–3 张（无门槛/满减/品类），**24h 到期**；封面只显示面额，点详情再领取。每个自然日每个用户最多弹一次。弹窗只在登录后第一次进用户端出现，底栏「首页 / 订单 / 我的」切换禁止再弹：`sessionStorage ssd.loginGiftShown` + 服务端 `GET /coupons/login-gift` 的 `{eligible:false, alreadyClaimed:true}`。当天已领 `LOGIN_GIFT:{userId}:{date}` 或 localStorage `ssd.loginGift:{userId}:{date}` 不再弹。关掉未领则记 `ssd.loginGiftDismissed:{userId}:{date}`，当日不再烦；可在「红包卡券」入口再领。不要在 `onMounted`/`onActivated` 无条件弹。
- 首页券区每 60s（或每次进首页）向 `home-feed` 取当前档，额度与图标随时间片变。
- 很久没登录（`last_login_at` ≥14 天）：大额池随机 1–2 张（满 30 减 20、满 50 减 25），scene=`LAPSED`，3 天到期。
- 用券频繁或完成单多（已用券≥3 或完成单≥8）：常规券满 25 减 5 / 满 40 减 8，scene=`FREQUENT`，3 天。
- 普通回流：满 20 减 6 / 满 35 减 10，scene=`RETURN`，3 天。
- 同一自然日同一 scene 不重复刷。会员红包额外无门槛，须开通会员，**到期到当周周日**。
- 过期券结算 `CouponPolicy` 判定不可用。

配送半径：默认 5km（`ssd.recommend.max-km` / `SSD_RECOMMEND_MAX_KM`）。结算 `POST /orders` 与 `preview` 超距 **409** `OUT_OF_RANGE`「超配送范围不可下单」。

闪会员：徽章主文案固定 **闪会员**（可加 `·金卡` / Lv），年费「年」标。不要用含糊「会员」当主标。`MemberBadge.vue` 统一用于个人中心、评论、订单评价；CSS 流光 + 轻微呼吸，年卡金边微光，评论 compact 只保留微光。个人中心「我的」参考美团：最上头像昵称 + 闪会员大卡，其下宫格入口（红包卡券数量、评价、地址、闪会员），不要在「我的」堆券列表。退出登录仍在底部。地图骑手用形象 marker（`/images/markers/rider.svg`，Leaflet `L.icon`），不要绿点；用户/商家用不同小图标。订单详情、送达浮窗、骑手大厅：状态「商家备餐中」配备餐动图（`/images/status/cooking.svg` + CSS）。完成页不要状态时间线，只显示当前一句状态；已完成大字「已完成」/「已送达」+ 完成时间。评价走独立页 `/orders/:id/review`（大星可点、商家可配图、骑手只打星）。打赏为礼物宫格四档，不要自定义金额输入。登录页预填账号密码，顶部小胶囊切用户/骑手/商家，**不**做一键登录大卡、**不**自动提交。

用户端进行中配送（`PAID|ACCEPTED|ARRIVED|DELIVERING`）：右下角 `DeliveryFloat`，5s 轮询 `GET /orders` + `GET /orders/{id}/track` 的 `etaMs`，有 ETA 展示「预计 HH:mm 送达」，无 ETA 展示「商家备餐中」或「骑手正在赶往商家」。点开展开地图/骑手摘要/订单摘要，再进详情。底栏以上约 80px。骑手端不展示。

会员等级（代码 `MemberRules`，开通即 1 级；净实付=COMPLETED 累加 `pay_amount_cents`，从 COMPLETED 退到 REFUNDED 则扣回）：

| 等级 | 累计净实付（元） | 称号 |
|---|---|---|
| 1 | 0 | 体验会员 |
| 2 | ≥200 | 银卡 |
| 3 | ≥800 | 金卡 |
| 4 | ≥2000 | 白金 |
| 5 | ≥5000 | 钻石 |
| 6 | ≥12000 | 黑金 |
| 7 | ≥30000 | 王者 |

年费用户 `yearMember=true` 金色「年」角标；过期灰显「已过期」，不能领会员红包，等级保留。前端 `MemberBadge.vue` 主文案 **闪会员**（可加 ·金卡），用于个人中心头像旁、店铺评价、我的评价、订单评价卡片，同一套组件，不要有的有有的没有。

活动（幂等键**服务端**拼装，禁止客户端传 idempotency-key 当权威）

- `GET /activities`
- `GET /activities/{id}` 秒杀详情（倒计时 `countdownSeconds`、当前档 `currentSku`、`nextRotateAt`、`rotateMinutes` 默认 10、SKU 池、原价/秒杀价、`originStock` 展示库存、`remainStock` Redis 实时剩余、当前用户本档 `grabbed`/`orderId`）。按时间片从活动 SKU 池轮换展示，Lua 仍按 skuId 扣，**抢完不切换展示商品**。种子至少 6 个不同 SKU。
- `POST /activities/{id}/grab-coupon` 键：`活动ID + 用户ID + COUPON`
- `POST /activities/{id}/seckill` `{skuId}` 键：`活动ID + 用户ID + SECKILL + skuId`（当前档 currentSku + 当前用户；历史上抢过别的 SKU 不算这一档已抢）。Lua GET 到 -1 时回放这一次的订单（带 `orderId`），不要跳到无关历史单。抢成功后 **不要立刻 rotate**；换档只按 `ssd.seckill.rotate-minutes`（默认 10）或 `nextRotateAt`。抢完仍展示当前商品、库存减少、按钮「去支付/已抢到」。库存 0 提示售罄，不要说抢过了。
- `POST /orders/{orderId}/grab` 仅 RIDER；键：`订单ID + 骑手ID`

订单 / 履约 / 支付

- `POST /orders/preview` `{merchantId, addressId, items[{skuId,qty,priceCents}], couponId, clientPayCents}` 返回商品合计、运费策略报价、券抵扣、应付、`suggestedCouponId`、`couponOptions[]`（每张券抵扣后应付、是否可用、不可用原因如未满门槛、已过期）。`couponId` 省略则预览自动按最优券计价；`couponId=0` 表示不用券。最优：可用券里应付最低，相同则到期更近。选非最优也可下单，`clientPayCents` 仍校验。新客 15 元无门槛与会员无门槛券都参与。商家打烊或商品下架 409；**超配送半径 409** `OUT_OF_RANGE`
- `POST /orders` 同上；应付 = 店铺商品合计 + 运费策略 - 券抵扣；`clientPayCents` 不一致则 409。商家 `OFFLINE` 或商品下架则 409。秒杀价只走活动下单，不走店铺价。订单号 Leaf 号段。**店铺价**下单成功扣 `merchant_sku.stock`（原子 `stock>=qty`）；秒杀仍只走 Redis Lua，不扣商家库存。完成单回写 SKU `month_sales`（对外 `soldCount`）。未支付确认取消 / 商家拒单 / 退款同意则回补店铺库存。
- `POST /orders/{id}/pay` `{channel:WECHAT|ALIPAY, password}` 假收银台，密码 `147258`；成功 `CREATED→MERCHANT_PENDING`
- `POST /orders/{id}/mock-pay` 兼容旧入口，等价微信支付
- `GET /orders?size=5|10|20&page=1&q=&cursor&status&scene` 订单列表。`size` 仅允许 5/10/20，默认 10。`page` 从 1 起为页码分页（分片 scatter 后内存过滤再切片，禁止把全表丢给前端再切）。无 `page` 时仍可用游标深分页：`WHERE … AND id < :cursor ORDER BY id DESC LIMIT :size`，先查 ID 再按 ID 回表。`q` 服务端智能搜索：订单号（Leaf id）前缀或完整、状态中文/英文枚举（待支付、配送中、PAID…）、店铺名、商品名、地址关键词；骑手端快照里的商家名同样可命中。空 `q` 不过滤。骑手 `scene=hall` 只返回可抢（`PAID` 且未指派）+ 自己进行中（`ACCEPTED|ARRIVED|DELIVERING`）；`scene=done` 只返回自己的 `COMPLETED`。用户/商家不传 `scene`，仍按身份看全状态。返回 `items, size, page, total, hasNext, hasPrev, nextCursor, q, scene`。空结果 `items=[]`。
- `GET /orders/{id}` 含 `reviewed` / `reviewId` / `canTip` / `tipped` / `tipCents` / `tipGiftCode` / `tipGiftLabel` / `riderProfile`（有骑手时）
- `GET /orders/{id}/track` 接单后含骑手实时坐标、`etaMs`、两段路径点；有骑手时附简介摘要；完成后可打赏标志同上
- `GET /merchant/stats?range=7d|30d|1y` 或 `days=7|30|365` 仅商家；今日单量/成交额/进行中/完成/退款金额 + 曲线（分片 scatter 后内存聚合）。近七日/近一月按日，近一年按月。额外 `summary`（近窗口单量/GMV/退款/热销 Top3/高峰日文案）、`topSkus`、`peakDate`，数字只来自订单聚合。
- `GET /merchant/report.csv?range=7d|30d|1y` 仅商家；下载 CSV（日期、单量、GMV、完成、退款），与 stats 同一套聚合。
- `GET /rider/stats` 仅骑手；本月完成单运费/penalty 分列 + 完成单数
- `POST /orders/{id}/cancel` `{reasonCode, reasonText}` 未支付入取消中
- `POST /orders/{id}/cancel-confirm` `CANCELLING → CANCELLED`
- `POST /orders/{id}/refund` `{reasonCode, reasonText}`
- `POST /orders/{id}/refund-review` `{approve, reasonText}` 仅商家
- `POST /orders/{id}/merchant-accept` `MERCHANT_PENDING → PAID`
- `POST /orders/{id}/merchant-reject` `{reasonText}` 仅本店商家；`MERCHANT_PENDING → REFUNDING → REFUNDED`（拒绝接单并退款）
- `POST /orders/{id}/arrive` `ACCEPTED → ARRIVED`
- `POST /orders/{id}/deliver` 仅接单骑手，`ACCEPTED|ARRIVED → DELIVERING`
- `POST /orders/{id}/complete` `DELIVERING → COMPLETED`
- `POST /orders/{id}/review` `{score:1-5, content?, photoUrls?, riderScore?, skuIds?}` 仅 USER、仅 `COMPLETED` 且该单未评过；未完成 409；二次评价 409（`merchant_review.order_id` 唯一）。商家必选星，文字/配图可选（最多 3 张）；有骑手时 `riderScore` 必选星、不要骑手文字。评价后回写店铺 `rating_avg` / `rating_count`，骑手星回写 `rider_profile` 评分
- `POST /review-photos` multipart 评价配图，类似头像，存 `data/review-photos/`，返回 `{photoUrl}`；静态 `GET /api/review-photos/**`
- `POST /orders/{id}/tip` `{giftCode: WATER|MILKTEA|GIFT|CHICKEN}` 仅下单用户、仅 `COMPLETED`、须有 rider_id；每单最多一档（`order_tip.order_id` 唯一键），二次 409。禁止自定义 cents。成功累加 `rider_profile.tip_cents_total`

券计算：`AMOUNT` 满 `min_spend_cents` 减 `discount_cents`；`PERCENT` 满门槛后按 `percent_off` 对商品合计打折。运费仍走 `FreightStrategy`，不发明 SLA 数字。

调度 / 检索

- `POST /dispatch/route` `{riderLat, riderLon, merchantLat, merchantLon, userLat, userLon}` 返回两段路径 + 总 `cost` + `etaMs` + `algorithm`（`ASTAR|DIJKSTRA`）+ 展平 `points`。网关打 **order:18083**（`POST /api/dispatch/route` 与 `/internal/route`）。算法仍是 A*（两端有坐标）/ Dijkstra（缺启发式）。本机无 Neo4j 时用 classpath OSM 内存图，与 dispatch 模块同构，不是高德。
- `POST /dispatch/assign/{orderId}` 自动指派（order 内实现；附近骑手走 account）
- `GET /riders/nearby?lat&lon&radiusMeters|radius&onlineStatus&acceptStatus` 距离排序。网关打 **account:18081**。本机读 MySQL `rider` 的 lat/lon/online_status/accept_status，Haversine 过滤 radius 米，默认 ONLINE/IDLE。LBS 生产是 Canal+ES；本机无 ES 时 **MySQL 距离兜底**。

Lua 返回与 HTTP：`1` → 200 成功；`0` → 409 库存不足；`-1` → 200 **回放首次结果**（不是报错）。库存不足与重复必须分开，不用异常当控制流。

## 5. 高并发活动链路（必须按此职责拆，不能互相替代）

- **Caffeine**：活动配置、SKU 基础信息；TTL 5–15s + `maximumSize`；**库存不进本地缓存**。多实例不一致靠短 TTL，不做广播失效。
- **Redis + Lua**（一次 `EVAL`）：`校验幂等键 → 读库存 → 判断 → 扣减 → 写幂等键+TTL`。返回 `1 / 0 / -1`。TTL = 活动周期 + 冗余。
- **DB 唯一索引**兜底同一幂等键。
- **Redisson** 可重入锁 + 看门狗（30s 租约、约 10s 续期）。粒度：秒杀/抢券 `活动ID+商品ID`，抢单 `订单ID`。**库存扣减本身走 Lua，锁只保护必须互斥的复合逻辑**（例如抢单成功后改接单状态 + 发 MQ 的临界区）。禁止全局锁。
- **RabbitMQ**：Lua 成功后再投递持久化；消费者幂等（按 idempotency_key / 唯一键）。

抢单 Redis：订单维度「可抢库存」为 1，Lua 扣成 0 并写下单骑手；MQ 通知 order-service 迁到 `ACCEPTED`。

## 6. Neo4j 调度

生产（ssd-dispatch + Neo4j，本机内存不够时不启动 18084）：

- 路口节点：`Intersection {id, lat, lon, version}`
- 道路关系：`ROAD {roadId, baseTime, congestion, cost, version}`，双向各一条
- `cost = baseTime * congestion`；分钟级任务（默认 60s）`UNWIND + SET` **只改** `congestion` 与 `cost`，不碰拓扑
- 导入：`UNWIND + MERGE` 幂等；路网 `version` 变更整体重导，保留上一 version 可回滚
- **与 MySQL 无重叠字段、无业务双写**；路网离线导入
- 规划：`骑手 → 商家` 再 `商家 → 用户` 两段最短路
- 主算法：GDS A*，`latitudeProperty=lat`, `longitudeProperty=lon`，权重 `cost`
- 降级：目标缺坐标或启发式不可用 → GDS Dijkstra
- 不用 Cypher `shortestPath`（只按跳数）
- 动态拥堵数据源：用骑手上报位置的路段耗时做**演示聚合**（定时任务读最近位置窗口，更新途经边 congestion）；无真实轨迹时种子脚本写入模拟拥堵
- 本机无 Neo4j：`ssd-common` 的 `GridPathFinder` + OSM 内存图由 **order** 加载，接口仍是 `/api/dispatch/route`。算法同构（A*/Dijkstra），不是高德。

## 7. Canal + ES

- Canal 监听 `ssd_account.rider`：`lat, lon, online_status, accept_status, update_time, version`
- ES index `ssd_riders`：`riderId, location geo_point, onlineStatus, acceptStatus, updateTime, version`
- 同步延迟目标：秒级（面试口径 1s 内；本地不把观测值写成 SLA）
- 分区：按主键 hash，同一骑手有序
- 消费：文档 `version` 更旧则丢弃，避免乱序覆盖
- 兜底：定时全量校准，按 `update_time` 窗口比对 MySQL 与 ES 条数/时间，差异重投
- 本机无 ES / 不启动 18085：`GET /api/riders/nearby` 由 **account** 读 MySQL `rider` 做 Haversine 距离过滤与排序，保证大厅/指派可用

## 8. 服务间消息

- Exchange `ssd.activity`：`seckill.success` / `coupon.success` / `grab.success`
- Exchange `ssd.order`：`order.paid`（可选触发推荐骑手，不强制自动改状态）

秒杀成功消费者：order-service 创建订单 `CREATED`（带活动快照与幂等键），**不直接 PAID**。用户再 mock-pay。

## 9. 种子与环境

`CatalogSeeder`（account 启动）按店名 / `sku_key` 幂等扩到 **8 品类 × 至少 15 店 ≥120 家**、**每店至少 40 SKU ≥4800**。上海/同城正式店名，禁止「演示」。批量 JDBC `INSERT`（`rewriteBatchedStatements`），不要 4800 次单条 JPA flush。图片 **禁止 picsum / loremflickr**（国内常挂或返回 HTML 被当图片）。规则：

- 稳定本地：`frontend/public/images/{category}/shop-1..6.svg` 与 `dish-1..8.svg`，按 lock 取同品类池；有精确 `shop-{id}.jpg` / `dish-{id}.jpg` 时优先。种子写入 `/images/{cat}/shop-n.svg`，不要外链。
- `onerror` 先同品类本地池，再 `/images/ph/{category}.svg`，不要外链、不要灰块人像。前端绑定前若 URL 命中 loremflickr/picsum 也改写到本地池。
- 验收：抽查首页 8 栏目 + 推荐卡片都能出图；药店不得出现火锅。

种子账号密码统一 `demo123456`：

- 用户 `13800000001`（闪送达用户）。已开通**年卡金卡**（level=3，`paid_cents_net≥128000`，`yearMember=true`），并入账新客 15 元无门槛，便于看见徽章。
- 骑手 `13800000002`（在线 IDLE，有坐标，闪送达骑手）。`rider_profile` 写正式简介、`started_on=2022-03-18`、`on_time_rate=0.978`、完成单/评分阈值点亮勋章（百单达人 / 好评如潮 / 准点骑士 / 打赏之星）。勋章按完成单≥100、评分≥4.8 且评价≥20、准时率≥95%、累计打赏≥20 元授予，不写死无意义图标。合成 `order_tip` + 历史完成单抽几单已打赏；其余完成单可供用户再打赏一次。
- 商家 `13800000003`（有坐标，闪送达鲜生店长；`online_status=ONLINE`）

启动时 `MerchantHistorySeeder` 用 Leaf 取号回填该店历史订单到分片表（今日/近月/近一年，含完成、进行中、退款），幂等键 `SEED:HIST:*`；失败只打日志，不弄死进程。`TipHistorySeeder` 从历史完成单抽几单写入打赏。失败只打日志。首页 recommend 分页触底；分类页 size=15 分页。种子评价写入 `merchant_review`，店铺页不空。种子 `coupon_template` 与秒杀活动 ≥6 SKU。

环境变量：

- `SSD_JWT_SECRET`（≥32 字符）
- `SSD_MODE=auto|live|dry-run`
- `SSD_AMAP_KEY`（可选；有则高德 JS API。无 Key 用高德公开栅格瓦片，OSM 仅兜底）
- `SSD_RIDER_MAX_WORK_HOURS`（默认 8）
- `SSD_SECKILL_ROTATE_MINUTES`（默认 10；自测可临时改为 1）
- `SSD_RECOMMEND_MAX_KM`（默认 5；推荐过滤与结算超距共用）

`docker compose up -d` 起全部中间件。Gateway 对外唯一入口 `http://127.0.0.1:18080`。

## 10. 测试（并发正确性，不是容量）

必须有：

- 同 SKU 并发秒杀：库存不为负、成交数 = min(请求成功数, 库存)
- 同一用户重复秒杀/抢券：第二次回放首次结果，不扣第二次库存。秒杀幂等按 **当前 SKU**；同用户并发只成一单；库存 1 两人只有一人成功。不要把 P95/QPS 写进断言。
- 非法状态迁移 409
- Leaf 单测：同 tag 无重复 ID
- 分片：同一 orderId 多次路由到同一 ds/table
- A*：两端都有坐标；缺坐标走 Dijkstra
- ES 消费：旧 version 不覆盖新文档

- 必须有前端 Vitest（`frontend/npm test`）：API 客户端、路由守卫、状态文案、购物车、秒杀轮询、登录页冒烟。禁止把 `P95 250ms`、`200 QPS`、`1.2 万节点` 写进断言或当成环境必须达到的指标。
- 推荐打分单测：更近的店分更高；有历史时命中品类高于未命中；打烊降权但仍 > 0。外滩 vs 五角场直线距离 > 5km，超半径不 inRange。
- 券图标与 TTL 单测：无门槛 / 满减 / 会员 / 新客 / 品类 / 折扣；新客 7 天、登录礼 24h、常规 3 天。过期券 quote 不可用。
