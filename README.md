# 线上购药系统 (Medicine Online)

面向药店、患者、药师和配送员的在线药品购买与管理系统。支持药品浏览、购物车、在线支付、处方审核、配送管理和药师咨询。

## 功能模块

用户管理
- 注册/登录（普通用户、慢病用户）
- JWT 认证、个人信息、地址管理

药品浏览
- 分类导航（树形二级分类）、关键词搜索、价格/销量排序
- 药品详情、用户评价、平均评分

购物车
- 添加药品、修改数量、删除商品、实时金额计算

订单支付
- 创建订单（库存锁定、乐观锁防并发）
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
| 认证 | JWT (jjwt 0.9.1) + BCrypt |
| 安全 | JwtInterceptor（认证）+ RoleInterceptor（管理员/药师角色校验） |
| 事务 | @Transactional 保护下单/支付/取消/处方审核等关键流程 |
| 并发 | 乐观锁 version 字段 + 库存 lock/deduct/unlock 三段式 |

## 项目结构

```
server-java/
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
│   │   └── DataInitializer.java         # 启动时初始化用户/药品/评价
│   ├── security/
│   │   ├── JwtUtil.java                 # Token 生成/解析/刷新
│   │   ├── JwtInterceptor.java          # 登录认证拦截
│   │   └── RoleInterceptor.java         # 角色权限拦截（admin/pharmacist）
│   ├── entity/                          # 14 个实体类（Lombok @Data）
│   ├── mapper/                          # 14 个 Mapper 接口（MyBatis-Plus + 手写 SQL）
│   ├── service/                         # Service 层
│   │   └── impl/                        # 14 个 ServiceImpl
│   └── controller/                      # 11 个 Controller
│       ├── admin/                       # 管理员接口
│       └── pharmacist/                  # 药师接口
├── src/main/resources/
│   ├── application.yml                  # 端口/数据源/JWT/上传配置
│   └── db/schema.sql                    # 14 张表建库脚本 + 分类数据
└── src/test/java/com/medicine/service/  # 5 个 Service 单元测试
```

## 快速启动

环境要求：JDK 8+、MySQL 8.0、Maven 3.6+

1. 创建数据库并导入表结构

```bash
mysql -u root -p < server-java/src/main/resources/db/schema.sql
```

2. 修改数据库密码

编辑 `server-java/src/main/resources/application.yml`，将 `spring.datasource.password` 改为你的 MySQL 密码。

3. 启动应用

```bash
cd server-java
mvn spring-boot:run
```

应用启动后自动执行 DataInitializer 填充初始数据（管理员/药师/配送员账号 + 8 种药品 + 库存 + 评价）。

4. 打开前端

浏览器打开 `client/index.html`，或直接调 API：`http://localhost:3000/api/v1/health`

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
| 药品 | /medicines | 分类、搜索、排序、详情、评价 |
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

## 角色与权限

| 角色 | user_type | 权限 |
|------|-----------|------|
| 普通用户 | 0 | 浏览、下单、咨询、评价 |
| 慢病用户 | 1 | 普通用户 + 处方上传、用药提醒 |
| 管理员 | 2 | 药品管理、订单处理、数据统计 |
| 药师 | 3 | 处方审核、咨询回复 |
| 配送员 | 4 | 接单、配送状态更新 |
