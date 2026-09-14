# 线上购药系统 (Medicine Online)

面向药店、患者、药师和配送员的在线药品购买与管理系统。支持药品浏览、购物车、在线支付、处方审核、配送管理和药师咨询。

## 功能模块

用户管理
- 注册/登录（普通用户、慢病用户）
- JWT 认证、个人信息、地址管理

药品浏览
- 分类导航（树形二级分类）、关键词搜索、价格/销量排序
- 药品详情（走 Redis 缓存）、用户评价、平均评分

购物车
- 添加药品、修改数量、删除商品、实时金额计算

订单支付
- 创建订单（库存锁定、乐观锁 + Redis 预减防并发）
- 在线支付（微信/支付宝/银联）、订单状态跟踪（8 种状态）

处方管理
- 处方图片上传、药师审核（通过/驳回）
- 审核结果影响订单流转

配送管理
- 配送员接单、配送状态更新、实时追踪

药师咨询
- 用户提交用药咨询、药师在线回复

用药提醒
- 创建提醒计划，支持多个提醒时间点

数据统计（管理员）
- 销售趋势、药品销量排行、订单状态分布

## 技术栈

| 层级 | 技术 |
|------|------|
| 框架 | Spring Boot 2.7.18 |
| ORM | MyBatis-Plus 3.5.3.1 |
| 数据库 | MySQL 8.0，14 张表 |
| 缓存 | Redis 5.0+（Lettuce 连接池 + StringRedisTemplate，缓存与库存预减） |
| 认证 | JWT (jjwt 0.9.1) + BCrypt |
| 安全 | JwtInterceptor（认证）+ RoleInterceptor（管理员/药师角色校验） |
| 事务 | @Transactional 保护下单/支付/取消/处方审核等关键流程 |
| 并发 | Redis Lua 原子预减 + 乐观锁 version + 库存 lock/deduct/unlock 三段式 |

## Redis 缓存设计

详情接口 `GET /api/v1/medicines/{id}` 原本每次都要跑「三表 JOIN + 评价列表 + 平均分聚合」三条 SQL，
属于典型的读多写少热点接口，因此用 Cache Aside 模式加了一层缓存。

### 1. 缓存三大问题一次堵完

| 问题 | 做法 | 代码位置 |
|------|------|----------|
| 缓存穿透 | 查库确认不存在的 id 也写一个空值标记（TTL 120s），恶意 id 直接命中标记返回，不打 DB | `CacheKeys.NULL_MARK` |
| 缓存击穿 | 热点 key 失效瞬间只放一个线程回源重建，其余线程等待 50ms 后重读缓存；重建锁 10s 自动过期 | `MedicineCache.getOrLoad` |
| 缓存雪崩 | TTL = 基础 1800s + 0~300s 随机抖动，避免大批 key 同一时刻集体失效 | `MedicineCache.ttlWithJitter` |

分布式锁的释放用 Lua 脚本判断 value 后再删（`lua/unlock.lua`），
避免「A 的锁超时自动释放、B 拿到锁、A 执行完把 B 的锁删掉」这类误删。

### 2. 缓存一致性：先更库、再删缓存

药品更新 / 上下架时走 `先改数据库 → 删除缓存 → 延迟 500ms 再删一次`（延迟双删）。

- **为什么删缓存而不是更新缓存**：更新缓存每次写都要重算缓存值，且并发写下会顺序错乱
  （A 先写库、B 后写库，但 B 先更新缓存，缓存里就留了 A 的旧值）；删除是幂等的，下次读自然回源。
- **为什么先更库再删缓存**：反过来的话，删完缓存、库还没更新时，并发读会把旧值重新写进缓存。
- **延迟双删**：删完缓存后仍可能有并发读把刚读到的旧值写回，所以延迟再删一次。
  更彻底的做法是 MQ 重试删除或 Canal 订阅 binlog。

### 3. 哪些字段不进缓存

库存（`stock_quantity`）随时在下单/支付/取消中变化，**不写进缓存**，详情接口读取时实时回源（单条主键查询）。
评价列表在当前版本没有写入接口，所以可以一起缓存；如果后续开放用户评价，需要在评价写入时同步删除详情缓存。

### 4. 库存：Redis 拦并发，MySQL 保正确

```
下单 → Redis Lua 原子预减(可售库存)  →  通过  → MySQL 复核 + lockStock 锁定
                    ↓ 不足                              ↓ 失败
              直接快速失败，不打 DB              stockCache.rollback() 归还预减量
```

- MySQL 始终是库存真值，`乐观锁 version + lock/deduct/unlock 三段式` 保证**最终不超卖**；
- Redis 计数只是挡在 DB 前面的第一道闸门，把明知不足的并发请求快速失败掉；
- 两者是「拦截 + 兜底」的组合，不是二选一。

计数含义是**可售库存**（`stock_quantity - locked_quantity`），所以各环节增减必须对齐：

| 场景 | DB 操作 | Redis 计数 |
|------|---------|-----------|
| 下单 | `lockStock`（锁定 +qty） | `decrby qty` |
| 支付 | `deductStock`（总量、锁定量同减） | 不动（可售量不变） |
| 取消订单 / 处方驳回 | `unlockStock` | `incrby qty` |
| 管理员改库存 / 启动预热 | `update` | 按 DB 重置 |

两个容易踩的点，脚本里都处理了：

1. **key 未预热时**（`lua/deduct_stock.lua` 返回 -1）自动降级为纯 DB 校验，不会因为「没预热」就拒绝下单；
2. **回滚不能直接 `incrby`**：如果 key 已被删除（失效），`incrby` 会把库存凭空造出来，
   得到一个和数据库完全不符的计数。所以 `lua/rollback_stock.lua` 只在 key 存在时才回补。

启动时 `StockCacheWarmer` 会把 DB 的可售库存预热进 Redis，并**以 DB 为准覆盖写**，
避免 Redis 里残留上一次运行的旧计数与 DB 漂移。

### 5. 降级策略

缓存是不可靠依赖，所有 Redis 调用都做了 try-catch 降级：读缓存失败 → 直查 DB；
获取重建锁失败 → 直查 DB；写缓存失败 → 只记日志不影响本次返回。
Redis 挂掉时接口仍可用（会退化成无缓存版本），只是少了并发保护。

## 项目结构

```
server/
├── pom.xml
├── src/main/java/com/medicine/
│   ├── MedicineApplication.java         # 启动类
│   ├── common/
│   │   ├── Result.java                  # 统一响应 {code, message, data, timestamp}
│   │   ├── BusinessException.java       # 业务异常（code + httpStatus）
│   │   └── GlobalExceptionHandler.java  # 全局异常处理
│   ├── config/
│   │   ├── SecurityConfig.java          # BCrypt + 无状态 Session
│   │   ├── WebConfig.java               # CORS + 拦截器注册
│   │   ├── RedisConfig.java             # Lua 脚本注册为 Bean
│   │   └── DataInitializer.java         # 启动时初始化用户/药品/评价
│   ├── cache/
│   │   ├── CacheKeys.java               # Redis key 统一收口
│   │   ├── MedicineCache.java           # 药品详情 Cache Aside（穿透/击穿/雪崩 + 延迟双删）
│   │   ├── StockCache.java              # 库存 Lua 原子预减 / 回滚 / 同步
│   │   └── StockCacheWarmer.java        # 启动预热可售库存
│   ├── security/
│   │   ├── JwtUtil.java                 # Token 生成/解析/刷新
│   │   ├── JwtInterceptor.java          # 登录认证拦截
│   │   └── RoleInterceptor.java         # 角色权限拦截（admin/pharmacist）
│   ├── entity/                          # 14 个实体类（普通 POJO，手写 getter/setter）
│   ├── mapper/                          # 14 个 Mapper 接口（MyBatis-Plus + 手写 SQL）
│   ├── service/                         # Service 层
│   │   └── impl/                        # 14 个 ServiceImpl
│   └── controller/                      # 11 个 Controller
│       ├── admin/                       # 管理员接口
│       └── pharmacist/                  # 药师接口
├── src/main/resources/
│   ├── application.yml                  # 端口/数据源/Redis/JWT/缓存参数
│   ├── lua/                             # 库存预减、回滚、解锁 三个 Lua 脚本
│   └── db/schema.sql                    # 14 张表建库脚本 + 分类数据
└── src/test/java/com/medicine/          # 单元测试（下单库存校验、缓存读写、缓存失效）
```

## 快速启动

环境要求：JDK 8+、MySQL 8.0、Redis 5.0+、Maven 3.6+

1. 创建数据库并导入表结构

```bash
mysql -u root -p < server/src/main/resources/db/schema.sql
```

2. 修改数据库密码

编辑 `server/src/main/resources/application.yml`，将 `spring.datasource.password` 改为你的 MySQL 密码。

3. 启动 Redis（默认 localhost:6379，端口不同改 `spring.redis.*`）

```bash
redis-server
```

4. 启动应用

```bash
cd server
mvn spring-boot:run
```

应用启动后自动执行 DataInitializer 填充初始数据（管理员/药师/配送员账号 + 8 种药品 + 库存 + 评价），
随后由 StockCacheWarmer 把可售库存预热进 Redis。

5. 打开前端

浏览器打开 `client/index.html`，或直接调 API：`http://localhost:3000/api/v1/medicines`

## 验证缓存是否生效

```bash
# 1) 连打两次详情接口，观察 Redis 是否写入缓存
curl -s http://localhost:3000/api/v1/medicines/1 > /dev/null
curl -s http://localhost:3000/api/v1/medicines/1 > /dev/null
redis-cli keys 'med:detail:*'     # 应该有 med:detail:1
redis-cli ttl  med:detail:1       # 1800 + 0~300 的随机值

# 2) 防穿透：不存在的 id 也会留下空值标记
curl -s http://localhost:3000/api/v1/medicines/999999   # 404
redis-cli exists med:detail:999999                      # 1

# 3) 库存预减
redis-cli get stock:med:1                               # 启动预热后的可售库存

# 4) 命中率
redis-cli info stats | grep keyspace
```

性能对比（做压测时务必先造数据、提高并发，否则单机小并发下加缓存可能反而更慢，
因为多了一次网络往返）：

1. 药品表造 10 万条数据；
2. `application.yml` 里 `medicine.cache.enabled=false`，JMeter 200 并发打 60 秒药品详情接口，记 avg / P95 / P99 / 错误率；
3. 改回 `true` 重启，同样参数再打一遍；
4. 两组数据对比写进 README。

## 默认账号

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | admin123 | 管理员 |
| pharmacist | pharmacist123 | 药师 |
| delivery | delivery123 | 配送员 |

普通用户通过注册页面自行创建。

## API 接口

统一前缀：`/api/v1/`，认证方式：`Authorization: Bearer <token>`

| 模块 | 路径 | 说明 |
|------|------|------|
| 认证 | /auth | 注册、登录、刷新 Token |
| 用户 | /user | 个人信息、地址管理（手机号脱敏） |
| 药品 | /medicines | 分类、搜索、排序、详情（Redis 缓存）、评价 |
| 购物车 | /cart | 增删改查 |
| 订单 | /orders | 下单、支付、取消、确认收货 |
| 处方 | /prescriptions | 上传处方图片 |
| 配送 | /delivery | 配送员接单、状态更新 |
| 咨询 | /consultations | 用户咨询、药师回复 |
| 提醒 | /reminders | 用药提醒 CRUD |
| 管理员 | /admin/medicines、/admin/orders、/admin/statistics | 药品管理、订单处理、数据统计 |
| 药师 | /pharmacist/prescriptions、/pharmacist/consultations | 处方审核、咨询回复 |

## 数据库设计

14 张表：users、addresses、categories、medicines、inventory、reviews、cart_items、orders、order_items、payments、prescriptions、deliveries、consultations、medication_reminders

核心设计：
- 库存三段式：下单 lockStock → 支付 deductStock → 取消 unlockStock，防止超卖
- 订单乐观锁：version 字段，取消和确认收货用 `WHERE version=?` 防并发
- 处方药订单需药师审核通过后才进入配药环节
- 树形分类：categories 表 parent_id 自引用，支持二级分类

## Redis key 设计

| key | 类型 | 含义 | TTL |
|-----|------|------|-----|
| `med:detail:{medicineId}` | string(JSON) | 药品详情缓存，空串表示「不存在」标记 | 1800~2100s 随机 / 空值 120s |
| `lock:med:detail:{medicineId}` | string | 缓存重建互斥锁（value 为持有者 token） | 10s |
| `stock:med:{medicineId}` | string(int) | 药品可售库存计数 | 无（以 DB 为准，启动预热） |

## 角色与权限

| 角色 | user_type | 权限 |
|------|-----------|------|
| 普通用户 | 0 | 浏览、下单、咨询、评价 |
| 慢病用户 | 1 | 普通用户 + 处方上传、用药提醒 |
| 管理员 | 2 | 药品管理、订单处理、数据统计 |
| 药师 | 3 | 处方审核、咨询回复 |
| 配送员 | 4 | 接单、配送状态更新 |
