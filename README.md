# 🐍 Snake Backend

在线贪吃蛇对战平台后端，基于 Spring Boot 3.5 + WebSocket 实现多人实时对战。

👉 前端仓库：[qwq-no/snake](https://github.com/qwq-no/snake)  
🌐 在线演示：http://124.221.217.168

---

## 技术栈

| 组件 | 技术 |
|------|------|
| 框架 | Spring Boot 3.5.14 (Java 17) |
| 实时通信 | Spring WebSocket (原生, 无 STOMP) |
| 安全 | Spring Security + JWT 双 Token 认证 (jjwt 0.13) |
| 持久层 | MyBatis-Plus 3.5.16 + MySQL 8 |
| 缓存 | Spring Data Redis (Lettuce) |
| 消息队列 | ✅ 无外部 MQ — 自研 in-process 游戏循环 |
| 构建 | Maven |
| 压力测试 | JMeter 5.6.3 + Groovy JSR223 (原生 `java.net.http.WebSocket`) |

## 核心架构

### WebSocket 消息分发管道

```
客户端 WebSocket → CoreHandler.handleTextMessage()
  ├── type="connect"      → SessionContextService.registerSession()
  ├── type="heartbeat"     → SessionContextService.updateHeartbeat()
  ├── type="page_change"   → PageUtil.syncPage()  → 按目标页面推送快照
  ├── type="group_chat_send" → prepare 页 → 房间广播 / home 页 → Redis + 全局广播
  └── 按 pageType 分发       → home | select | prepare | online | single | talk
```

所有消息通过单一 WebSocket 端点 `/ws/game` 接入，`CoreHandler` 作为统一入口按 `type` + `pageType` 两级路由分发到对应的子 Handler。

### Game Tick 引擎 (核心亮点)

```
                    ┌── 客户端 input (fire-and-forget) ──→ directionQueue.addLast()
                    │                                       (WebSocket 线程, 不阻塞)
                    │
150ms tick 线程 ───┤  gameRoomExecutor.invokeAll(tasks, 140ms)
                    │   ├── synchronized(roomState) {        每房间串行
                    │   │     updateProps → updateSnakes → updateFruits
                    │   │     → handleRespawn → delEmojis
                    │   │     → broadcastRoomDelta(roomState) }
                    │   │
                    │   └── roomStateBroadcastExecutor 异步推送 ──→ 客户端
```

**关键设计决策**：
- **客户端不等待服务端响应**：input 消息 fire-and-forget，服务端 tick 决定何时消费
- **directionQueue 天然去抖动**：每 tick 消费队列中最后一个方向（~150ms 延迟），网络抖动自动平滑
- **Delta 增量推送**：tick 出口比较前后 `RoomSnapshotVO`，仅下发变化的蛇和水果，避免每帧推送 102×102 全量地图
- **synchronized(roomState)** 保护并发安全：WebSocket 线程（leave/join）和 game tick 线程串行化，消除 `ConcurrentModificationException`
- **bcrypt 线程隔离**：登录/注册的 bcrypt 跑在独立 `loginExecutor` (`ThreadPoolTaskExecutor`, 核心线程=CPU 核数×2)，通过 `Callable<>` 返回值释放 Tomcat 工作线程

### 服务器推送架构

`BroadcastServiceImpl` 中的 `roomStateBroadcastExecutor` 使用"一房间一任务"策略（而非"每用户一任务"），避免 700+ 并发任务打爆线程池。`isOpen()` 检查在 executor lambda 内部执行，消除 TOCTOU 竞态（session 在 submit 和 execute 之间断开）。

### 房间生命周期

```
waiting → (全部 ready) → playing → (倒计时结束) → finished → reset → waiting
   ↑                                                     │
   └──────────── 成员归零自动重置 ←───────────────────────┘
```

- 房间状态通过 `RoomStateManager` 集中管理，`ConcurrentHashMap` 保证并发安全
- 房间摘要列表通过 `room_summary_list`(全量) / `room_summary_delta`(增量) 推送给 select 页用户
- 游戏中加入的玩家通过 `assignHumanSnakeToNewPlayer` 获得蛇控制权，`synchronized(roomState)` 防护

### 安全模型

- **双 Token 认证**：accessToken (localStorage, 短期) + refreshToken (httpOnly cookie, 长期)
- **自动续签**：Axios 拦截器在 401 时自动调 `/api/refresh/login` 刷新 accessToken
- **JWT 无状态**：`SecurityContextHolder` 直接设认证，不查库，零 session 开销
- **CORS 通配**：`addAllowedOriginPattern("*")` + `allowCredentials(true)` 兼容动态域名部署

## 项目结构

```
snake_back/src/main/java/com/example/snake_back/
├── SnakeBackApplication.java          # @SpringBootApplication + @EnableScheduling
├── common/
│   ├── filter/
│   │   └── JwtAuthFilter.java         # OncePerRequestFilter, 白名单 + JWT 解析
│   ├── result/
│   │   └── Result.java               # 统一响应体 {code, msg, data}
│   └── utils/
│       ├── JwtUtil.java              # access + refresh token 生成/验证
│       ├── PageUtil.java             # 页面切换 → 快照推送 + 好友状态广播
│       └── RoomUtil.java             # 房间初始化/重置/满员检查
├── config/
│   ├── SecurityConfig.java           # Spring Security 无状态配置
│   ├── CorsConfig.java               # CORS 全局过滤器
│   ├── WebSocketConfig.java          # /ws/game 端点注册 + 连接限流
│   └── AsyncConfig.java              # bcrypt 线程池隔离
├── controller/
│   ├── UserController.java           # login/register/getId/updatePassword (Callable<>)
│   ├── RoomController.java           # 房间列表查询
│   ├── RefreshTokenController.java   # refresh token 续签/注销
│   ├── FriendshipController.java     # 好友列表/删除
│   └── FriendRequestController.java  # 好友申请/处理
├── service/
│   ├── Impl/
│   │   ├── OnlineServiceImpl.java    # gameTick(), input(), leaveRoom() — 游戏核心
│   │   ├── BroadcastServiceImpl.java # 全量快照 + Delta 增量 + 房间聊天广播
│   │   ├── PrepareServiceImpl.java   # 倒计时 → initGame → 第一帧快照
│   │   └── SelectServiceImpl.java    # joinRoom → 房间摘要推送
│   ├── BroadcastService.java         # 16 个广播接口
│   └── … (其他 Service 接口)
├── websocket/
│   ├── CoreHandler.java              # WebSocket 统一入口: 按 type + pageType 分发
│   ├── OnlineHandler.java            # online 页面: input/leave/emoji 处理
│   ├── PrepareHandler.java           # prepare 页面: ready/unready
│   ├── SelectHandler.java            # select 页面
│   ├── HomeHandler.java              # home 页面: 好友系统
│   └── ConnectionLimitInterceptor.java
├── manager/
│   ├── RoomStateManager.java         # RoomState + 房间成员 ConcurrentHashMap
│   ├── RoomSummaryManager.java       # 房间摘要列表 + memberChange
│   ├── SessionContextManager.java    # WebSocket session → userCode 映射
│   └── GroupChatManager.java         # 全局群聊 (Redis 500条上限)
└── pojo/
    ├── dto/
    │   ├── RoomState.java            # 房间状态: snakes/fruits/map/props/emojis
    │   ├── SnakeState.java           # 蛇状态: body/directionQueue/respawnTimer/props
    │   └── SessionContextDTO.java    # session → userCode/pageType/roomCode
    └── vo/
        ├── RoomSnapshotVO.java       # 全量快照 (102×102 地图 + 蛇列表)
        ├── RoomDeltaVO.java          # 增量变化: 变化的蛇 + 增减的水果
        └── …
```

## 快速开始

```bash
# 环境要求: Java 17, MySQL 8, Redis
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE snake CHARACTER SET utf8mb4"
mysql -u root -p snake < 建表.sql

# 2. 修改 application.yml 中的数据库连接（或通过环境变量覆盖）
#    DB_URL / DB_USER / DB_PASS / REDIS_HOST

# 3. 启动
cd snake_back
./mvnw spring-boot:run

# 4. 验证
curl http://localhost:8086/api/user/login -X POST \
  -H "Content-Type: application/json" \
  -d '{"username":"1","password":"1"}'
```

## 压力测试

使用 JMeter 5.6.3 + 原生 `java.net.http.WebSocket`（无第三方插件）完成全链路压测。

### HTTP API 基线

| 并发 | TPS | 错误率 |
|------|-----|--------|
| 50 | 471 | 0% |
| 200 | 3,716 | 0% |
| 500 | 9,253 | 0% |

### WebSocket 游戏对战 — 承载上限

**20 房间 × 35 玩家 = 700 并发**，稳态 input 吞吐 182/s，gameTick 耗时 0-4ms，无掉房。

测试脚本位于 `test/jmeter/jmx/`，包括连接/房间/游戏/群聊/好友全场景。

## 部署 (腾讯云 Ubuntu)

```
浏览器 → http://124.221.217.168:80 (Nginx)
  ├── /        → Vue 前端静态文件 (try_files $uri /index.html)
  ├── /api/*   → proxy_pass http://127.0.0.1:8086
  └── /ws/*    → proxy_pass http://127.0.0.1:8086 (Upgrade: websocket)
```

环境变量覆盖：`DB_URL`, `DB_USER`, `DB_PASS`, `REDIS_HOST`, `APP_CORS_ORIGINS`。

---

👈 前端代码见 [qwq-no/snake](https://github.com/qwq-no/snake) | 在线演示：http://124.221.217.168
