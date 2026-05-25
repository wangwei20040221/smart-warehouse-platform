# 星云智仓 — 全栈智能仓储管理与 AI 对话平台

## 项目简介

企业级智能仓储管理系统（WMS）+ IoT 设备监控 + AI 智能助手 + 移动端应用，采用前后端分离架构，基于 Jeecg-Boot 3.8.1 低代码平台深度定制开发。

- **后端**：Spring Boot 3.4.5 + MyBatis-Plus 3.5.12，覆盖仓储全业务流程（入库/出库/库存/波次/拣货/发货/快递），集成 AI Function Calling + RAG 向量检索，IoT 设备 MQTT 通信 + 时序数据存储
- **前端**：Vue 3 + Vite 7 + Vant 4 移动端，集成 AI 大模型 SSE 流式对话

---

## 项目结构

```
星云智仓/
├── backend/                                      # 后端 Maven 多模块项目
│   ├── pom.xml                                   #   父 POM，聚合所有子模块
│   ├── db/                                       #   数据库脚本
│   │   ├── xingchenwms-20250912.sql               #     完整数据库 Dump（16,141 行，120+ 张表）
│   │   └── xingchenwms-20251114.sql               #     更新版数据库 Dump（16,743 行）
│   │
│   ├── jeecg-boot-base-core/                      #   核心框架层
│   │   └── src/main/java/org/jeecg/
│   │       ├── common/                            #     通用工具（加密/脱敏/OSS/ES/异常/SQL 解析）
│   │       ├── config/                            #     核心配置（Shiro 安全、WebSocket、签名验证、
│   │       │                                       #       SQL 防火墙、MyBatis 拦截器、Redisson 分布式锁、
│   │       │                                       #       Swagger、Druid 数据源、OSS/MinIO）
│   │       └── modules/base/                      #     基础服务（Mapper、Service 实现）
│   │
│   ├── jeecg-module-system/                       #   系统管理模块
│   │   ├── jeecg-system-api/                      #     系统 API 接口层
│   │   │   ├── jeecg-system-cloud-api/            #       Feign 远程调用接口
│   │   │   └── jeecg-system-local-api/            #       本地 API 接口
│   │   ├── jeecg-system-biz/                      #     系统业务实现层
│   │   │   └── src/main/java/org/jeecg/modules/
│   │   │       ├── system/                        #       用户/角色/部门/权限/字典/日志/租户/公告
│   │   │       ├── message/                       #       消息推送（WebSocket + 定时任务）
│   │   │       ├── quartz/                        #       Quartz 定时任务管理
│   │   │       ├── oss/                           #       文件上传（本地/MinIO/阿里云 OSS）
│   │   │       ├── openapi/                       #       OpenAPI 外部接口 + Swagger 自动生成
│   │   │       ├── monitor/                       #       系统监控（Redis/Memory/HTTP Trace）
│   │   │       ├── cas/                           #       CAS 单点登录
│   │   │       └── api/                           #       外部 API 控制器
│   │   └── jeecg-system-start/                    #     启动入口（Spring Boot 主类 + Flyway 迁移）
│   │       └── src/main/resources/
│   │           ├── application.yml                #       主配置（激活 dev profile）
│   │           ├── application-dev.yml            #       开发环境（MySQL/Redis/PostgreSQL/MQTT/RabbitMQ）
│   │           ├── application-test.yml           #       测试环境
│   │           ├── application-prod.yml           #       生产环境（安全加固）
│   │           ├── application-dm8.yml            #       达梦数据库适配
│   │           ├── application-kingbase8.yml      #       人大金仓 Kingbase8 适配
│   │           ├── flyway/sql/mysql/              #       Flyway 数据库迁移脚本
│   │           │   ├── V3.8.0_1__airag_add_menu.sql   #   AI 菜单初始化
│   │           │   └── V3.8.0_2__airag_init_db.sql    #   AI RAG 表结构+初始数据
│   │           └── jeecg/code-template-online/    #       代码生成器模板
│   │
│   ├── jeecg-module-wms/                          #   WMS 仓储核心业务模块
│   │   ├── pom.xml
│   │   └── src/main/java/org/jeecg/modules/wms/
│   │       ├── goods/                             #     商品/货主/品牌/分类/包装材料/承运商管理
│   │       │   ├── controller/                    #       7 个 REST 控制器
│   │       │   ├── entity/                        #       实体类
│   │       │   ├── mapper/                        #       MyBatis Mapper
│   │       │   └── service/                       #       业务服务
│   │       ├── warehouse/                         #     仓库三层空间模型（仓库 → 库区 → 库位）
│   │       │   ├── controller/                    #       3 个 REST 控制器
│   │       │   ├── entity/                        #       WmsWarehouses / WmsStorageZones / WmsStorageLocations
│   │       │   ├── mapper/                        #       MyBatis Mapper
│   │       │   └── service/                       #       业务服务
│   │       ├── inorder/                           #     入库管理（入库单 → 收货 → 上架）
│   │       │   ├── controller/                    #       WmsStockInOrders / ReceiveTasks / PutawayTasks
│   │       │   ├── entity/mapper/service/         #       分层架构
│   │       │   └── vo/                            #       视图对象
│   │       ├── inventory/                         #     库存管理（四维模型 + 5 种变更策略）
│   │       │   ├── controller/                    #       WmsInventory / WmsInventoryTrans
│   │       │   ├── entity/                        #       库存实体（库存量/可用量/分配量/是否可售）
│   │       │   ├── mapper/service/                #       分层架构
│   │       │   └── vo/                            #       库存 VO
│   │       ├── outorder/                          #     出库管理（出库单 → 分配 → 拣货确认）
│   │       │   ├── controller/                    #       WmsOutOrders
│   │       │   ├── entity/                        #       出库单/出库明细/分配记录
│   │       │   ├── mapper/service/                #       分层架构
│   │       │   └── vo/                            #       出库 VO
│   │       ├── wave/                              #     波次管理（策略模式 + 责任链）
│   │       │   ├── controller/                    #       WmsWaveMaster / WaveStrategy / PickingTasks
│   │       │   ├── entity/                        #       波次主表 / 波次 SKU 汇总 / 波次策略
│   │       │   ├── strategy/                      #       策略链实现类
│   │       │   ├── mapper/service/                #       分层架构
│   │       │   └── vo/                            #       波次 VO
│   │       ├── shipment/                          #     发货拆包（策略模式，按重量上限 20kg 贪心拆分）
│   │       │   ├── controller/                    #       WmsShipment
│   │       │   ├── strategy/                      #       拆包策略实现
│   │       │   ├── entity/mapper/service/vo/      #       分层架构
│   │       ├── pickroute/                         #     拣货路径优化（A* 算法）
│   │       │   ├── dto/                           #       路径点 DTO
│   │       │   └── service/                       #       A* 寻路算法实现
│   │       ├── waybill/                           #     快递运单（顺丰 API 对接）
│   │       │   ├── controller/                    #       WmsWaybill
│   │       │   ├── sf/                            #       顺丰 SDK 封装（下单/查询/面单打印/文件下载）
│   │       │   ├── service/                       #       运单服务
│   │       │   └── task/                          #       运单异步任务
│   │       ├── ai/chat/                           #     AI 智能助手（Function Calling + RAG + SSE 流式）
│   │       │   └── controller/                    #       ChatController（/ai/chat、/ai/chat/send）
│   │       ├── analysis/                          #     大屏数据看板
│   │       │   ├── controller/                    #       BigscreenController
│   │       │   └── mapper/service/vo/             #       分析数据层
│   │       ├── wmstask/                           #     仓储任务管理（任务分配/执行记录）
│   │       ├── websocket/                         #     WMS WebSocket（大屏实时推送）
│   │       ├── config/                            #     WMS 配置（顺丰/波次策略/线程池/PageHelper）
│   │       ├── designmode/                        #     设计模式教学示例（策略模式）
│   │       ├── designmode2/                       #     设计模式教学示例（责任链）
│   │       └── lock/                              #     分布式锁演示
│   │
│   ├── jeecg-module-iot/                          #   IoT 物联网模块
│   │   ├── jeecg-iot-base/                        #     IoT 基础设施
│   │   │   └── src/main/java/org/jeecg/iot/base/
│   │   │       ├── config/                        #       MQTT/RabbitMQ/InfluxDB 连接配置
│   │   │       └── mqtt/                          #       MQTT 客户端 + 消息处理器
│   │   ├── jeecg-iot-manage/                      #     IoT 设备管理
│   │   │   └── src/main/java/org/jeecg/modules/iot/manage/
│   │   │       ├── controller/                    #       7 个 REST 控制器（设备/产品/物模型/监控）
│   │   │       ├── service/                       #       业务服务层
│   │   │       ├── mqtt/processor/                #       MQTT 消息处理器
│   │   │       ├── websocket/                     #       IoT 设备 WebSocket 推送
│   │   │       ├── sender/                        #       消息发送器
│   │   │       ├── task/                          #       定时任务
│   │   │       └── demo/ai/service/               #       AI 演示服务（DashScope 直接调用）
│   │   ├── jeecg-iot-alert/                       #     IoT 告警服务
│   │   │   └── src/main/java/org/jeecg/modules/iot/alert/
│   │   │       ├── listener/                      #       RabbitMQ 告警消息监听器
│   │   │       ├── entity/                        #       告警规则/告警日志实体
│   │   │       ├── service/                       #       告警判定服务
│   │   │       └── dto/                           #       告警 DTO
│   │   └── jeecg-iot-simulate/                    #     IoT 设备模拟器
│   │       └── src/main/java/org/jeecg/modules/iot/simulate/
│   │           ├── config/                        #       设备信息配置（MQTT broker/设备列表/传感器样本）
│   │           ├── service/                       #       设备模拟服务（定时上报/规则控制）
│   │           └── dto/                           #       模拟数据 DTO
│   │
│   ├── jeecg-boot-module/                         #   通用模块
│   │   ├── jeecg-module-demo/                     #     示例代码
│   │   │   └── src/main/java/org/jeecg/modules/demo/
│   │   │       ├── test/controller/               #       在线表单/订单/Demo 演示
│   │   │       ├── gpt/controller/                #       AI 演示控制器
│   │   │       ├── mock/                          #       Mock API + VXE WebSocket
│   │   │       ├── cloud/                         #       XXL-Job + Feign 云平台演示
│   │   │       └── dlglong/controller/            #       第三方 Mock 接口
│   │   └── jeecg-boot-module-airag/              #     AI RAG 向量检索模块
│   │       └── src/main/java/org/jeecg/modules/airag/
│   │           ├── app/                           #       AI RAG 应用（应用管理/聊天/对话记录）
│   │           ├── llm/                           #       LLM 模型管理（模型配置/知识库/文档处理）
│   │           └── config/                        #       Spring AI 配置（ChatClient/pgvector）
│   │
│   └── wms_iot_server/                            #   IoT 服务端部署目录
│
├── frontend/                                      # 前端项目（Vue 3 移动端）
│   ├── package.json                               #   依赖配置
│   ├── vite.config.js                             #   Vite 构建配置
│   └── src/
│       ├── main.js                                #   应用入口
│       ├── App.vue                                #   根组件
│       ├── style.css                              #   全局样式
│       ├── config/
│       │   └── api.js                             #   API 端点 + AI 配置
│       ├── router/
│       │   └── index.js                           #   路由配置（12 条路由 + 导航守卫）
│       ├── i18n/
│       │   ├── index.js                           #   i18n 初始化
│       │   └── locales/
│       │       ├── zh-CN.js                       #   中文翻译
│       │       └── en-US.js                       #   英文翻译
│       ├── store/
│       │   ├── index.js                           #   Pinia Store 入口
│       │   ├── user.js                            #   用户状态（登录 Token / localStorage 持久化）
│       │   ├── theme.js                           #   主题切换（4 套配色方案）
│       │   ├── language.js                        #   语言切换
│       │   └── modules/
│       │       ├── news.js                        #   新闻数据（分类列表 + 分页 + 详情）
│       │       ├── favorite.js                    #   收藏（API + localStorage 双写）
│       │       └── history.js                     #   浏览历史
│       ├── views/
│       │   ├── Home.vue                           #   首页（分类 Tab + 下拉刷新 + 无限滚动）
│       │   ├── AIChat.vue                         #   AI 对话（SSE 流式 + Markdown 渲染 + 打字指示器）
│       │   ├── Category.vue                       #   分类浏览
│       │   ├── NewsDetail.vue                     #   新闻详情
│       │   ├── Login.vue                          #   用户登录
│       │   ├── Register.vue                       #   用户注册
│       │   ├── My.vue                             #   个人中心
│       │   ├── Profile.vue                        #   个人信息编辑
│       │   ├── Settings.vue                       #   主题 / 语言设置
│       │   ├── History.vue                        #   浏览历史
│       │   └── Favorite.vue                       #   我的收藏
│       └── components/
│           ├── TabBar.vue                         #   底部导航栏（首页/AI/我的）
│           ├── NewsItem.vue                       #   新闻卡片组件
│           └── HelloWorld.vue                     #   Hello World 示例
│
├── .gitignore                                     # Git 忽略规则
└── README.md                                      # 本文件
```

---

## 技术栈

### 后端

| 类别 | 技术 | 版本 |
|------|------|------|
| **核心框架** | Spring Boot | 3.4.5 |
| **ORM** | MyBatis-Plus | 3.5.12 |
| **安全** | Apache Shiro + JWT + Redis | — |
| **数据库** | MySQL（主库）、PostgreSQL + pgvector（向量库）、InfluxDB（时序库） | — |
| **缓存** | Redis | — |
| **消息队列** | RabbitMQ | — |
| **物联网协议** | MQTT（EMQX Broker） | — |
| **AI 框架** | Spring AI（ChatClient、Function Calling、RAG、pgvector） | — |
| **AI 模型** | 阿里云 DashScope（qwen3-max）、text-embedding-v4、Ollama（deepseek-r1:7b） | — |
| **定时任务** | Quartz（JDBC 集群模式） | — |
| **分布式锁** | Redisson | — |
| **数据库迁移** | Flyway | — |
| **连接池** | Druid | — |
| **API 文档** | Knife4j（Swagger 增强） | — |
| **对象存储** | 阿里云 OSS / MinIO / 本地存储 | — |
| **HTTP 服务器** | Undertow | — |
| **构建工具** | Maven（多模块） | — |
| **JDK** | Java 17 | — |

### 前端

| 类别 | 技术 | 版本 |
|------|------|------|
| **核心框架** | Vue 3（Composition API） | — |
| **构建工具** | Vite | 7 |
| **UI 组件库** | Vant | 4 |
| **状态管理** | Pinia（localStorage 持久化） | 3 |
| **路由** | Vue Router | 4 |
| **国际化** | vue-i18n | — |
| **Markdown 渲染** | marked + DOMPurify（XSS 过滤） | — |
| **HTTP 客户端** | Fetch API（SSE 流式读取） | — |
| **代码检查** | ESLint | — |

---

## 核心功能

### 一、仓储管理（WMS）

#### 1. 基础数据管理
- **仓库三层空间建模**：仓库（Warehouse）→ 库区（Storage Zone）→ 库位（Storage Location）
- **商品主数据**：商品（Products）、品牌（Brand）、分类（Categories）、图片（Images）
- **供应链数据**：货主（Cargo Owners）、承运商（Carrier）、包装材料（Packaging Material）

#### 2. 入库管理
- 入库单创建 → 收货确认 → 上架任务分配
- 收货任务（Receive Tasks）：按入库单维度操作
- 上架任务（Putaway Tasks）：扫描库位码确认上架
- 自动库存变更记录（库存流水表）

#### 3. 库存管理
- **四维库存模型**：`库存量 | 可用量 | 分配量 | 是否可售`
- **5 种变更策略**（策略模式）：采购入库 / 退货入库 / 销售出库 / 调拨出库 / 盘点调整
- 批次号管理 + 库存流水追踪
- 缺货登记

#### 4. 出库管理
- 出库单创建 → 库存分配 → 拣货确认 → 发货 → 快递面单
- 按批次先进先出（FIFO）分配
- 拣货任务（Picking Tasks）管理

#### 5. 波次管理（策略模式 + 责任链）
- 波次策略配置（按库区 / 按品类 / 按优先级等维度）
- 波次拆分：将多个出库单按策略合并为一个波次
- 责任链模式：策略链依次执行，每种策略处理符合条件的出库单
- 波次 SKU 汇总

#### 6. 发货拆包（策略模式）
- 按重量上限 20kg 贪心算法自动拆分包裹
- 发货明细管理

#### 7. 拣货路径优化（A\* 算法）
- 仓库库位坐标建模
- A\* 寻路算法计算最优拣货路径
- 减少拣货员行走距离

#### 8. 快递对接（顺丰）
- 顺丰开放平台 API 集成
- 电子面单下单 / 订单查询 / 面单 PDF 打印 / 面单文件下载
- 运单号管理

#### 9. 大屏数据看板
- 仓储核心数据实时展示
- WebSocket 推送数据更新

---

### 二、AI 智能助手

#### 1. 三层 AI 能力

| 层级 | 功能 | 说明 |
|------|------|------|
| **纯对话** | 通用 AI 问答 | 直接调用 LLM 对话 |
| **对话 + RAG** | 知识库增强检索 | pgvector 向量相似度检索 → 上下文注入 → LLM 生成回答 |
| **对话 + RAG + Tool** | Function Calling | AI 自主决策调用工具函数，查询实时库存/仓库数据 |

#### 2. 技术架构

```
用户消息 → ChatController
  ├─ 纯对话模式 → ChatClient.call()
  ├─ RAG 模式 → pgvector 向量检索（HNSW + Cosine Distance）
  │             → 检索结果拼入 Prompt → ChatClient.call()
  └─ Tool 模式 → ChatClient.call() + @Tool 注解方法
                  ├─ queryInventory() → MySQL 实时库存查询
                  └─ queryWarehouses() → 仓库列表查询
```

#### 3. Function Calling 工具

| 工具方法 | 功能 | 注解 |
|----------|------|------|
| `queryInventory()` | 按商品名/编码/货主/仓库/库位查询库存 | `@Tool` |
| `queryWarehouses()` | 查询所有仓库列表 | `@Tool` |

#### 4. 向量检索（RAG）
- **嵌入模型**：阿里云 text-embedding-v4（768 维向量）
- **向量存储**：PostgreSQL + pgvector 扩展
- **索引**：HNSW 索引 + Cosine Distance 余弦距离
- **工具类**：`VectorDistanceUtils`（欧氏距离 / 余弦相似度）

#### 5. 支持的 LLM 模型
- 阿里云 DashScope：qwen3-max、qwen3-vl-flash
- DeepSeek：deepseek-chat
- OpenAI 兼容：gpt-4o-mini、text-embedding-ada-002
- Ollama 本地部署：deepseek-r1:7b
- 智谱 AI：glm-4-flash、Embedding-3

#### 6. 自定义 ChatModel 适配
- `AlibabaOpenAiChatModel`（788 行）：自定义适配阿里云 DashScope 的 OpenAI 兼容 API，支持同步/流式调用 + Tool Calling

#### 7. SSE 流式输出
- 前端 `AIChat.vue`：`fetch + ReadableStream.getReader()` 逐字读取 SSE 流
- `marked + DOMPurify`：Markdown 渲染 + XSS 安全过滤
- 支持 `<think>` 标签处理（推理模型思考过程折叠）
- 打字指示器动画

---

### 三、IoT 物联网

#### 1. 设备管理
- **物模型（Product Model）**：定义设备属性/事件/服务
- **产品（Product）**：产品类型注册
- **设备（Device）**：设备注册/认证/状态管理
- **监控配置（Monitor Config）**：设备监控参数配置

#### 2. 数据流架构

```
设备 → MQTT（EMQX Broker）→ MQTT Client（订阅 wms/iot/report）
     → RabbitMQ（消息解耦）→ InfluxDB（时序数据存储）
                            → 告警服务（规则匹配 → 告警生成）
     → WebSocket → 前端实时展示
```

#### 3. 设备模拟器
- 可配置设备列表（设备名、设备编码、传感器样本数据）
- 支持上报模式：`loop`（循环）、`back_and_forth`（往返）
- 可配置上报频率（秒级）
- 支持 MQTT 认证密码

#### 4. 告警服务
- 告警规则配置（阈值/条件）
- RabbitMQ 消息监听器：消费设备数据 → 规则匹配 → 生成告警日志
- 支持手动确认（Acknowledge Mode: Manual）

#### 5. 时序数据存储（InfluxDB）
- Bucket：`wms-iot`
- 存储设备上报的温度、湿度等传感器数据
- 支持按时间范围查询

---

### 四、系统管理

#### 1. 权限管理
- **RBAC 模型**：用户 → 角色 → 权限
- **数据权限**：部门数据隔离
- **租户管理**：多租户 SaaS 支持
- **JWT 认证**：无状态 Token + Redis 缓存

#### 2. 系统功能
- 用户/角色/部门/岗位管理
- 菜单/按钮权限管理
- 数据字典维护
- 系统日志（操作日志/数据日志）
- 系统公告/消息推送
- 文件上传（本地/MinIO/阿里云 OSS）
- 代码生成器（Online 低代码开发）

#### 3. 定时任务（Quartz）
- JDBC 持久化存储，集群部署支持
- 可视化 Cron 管理界面
- 任务日志追踪

#### 4. 第三方集成
- **第三方登录**：GitHub / 微信企业号 / 钉钉 / 微信开放平台（JustAuth）
- **消息推送**：WebSocket + 定时任务
- **OpenAPI**：外部接口 + Swagger 文档自动生成
- **CAS 单点登录**
- **XXL-Job** 分布式任务调度
- **百度开放 API**

#### 5. 安全机制
- Shiro + JWT 无状态认证
- 签名验证（防篡改）
- SQL 注入防火墙
- 低代码模式安全控制（dev/prod）
- 数据脱敏

---

### 五、前端应用

#### 1. 移动端新闻资讯
- 首页分类 Tab（van-tabs）
- 下拉刷新（van-pull-refresh）+ 无限滚动（van-list）
- 新闻详情页
- 分类浏览

#### 2. AI 对话
- SSE 流式接收 AI 回复
- Markdown 渲染（marked + DOMPurify）
- 消息气泡样式
- 输入框 + 发送按钮

#### 3. 用户中心
- 登录/注册/个人信息编辑
- 收藏（双写：API + localStorage）
- 浏览历史
- 4 套主题切换
- 中英双语国际化

---

## 数据库表概览

### 核心 WMS 业务表（27 张）

| 表名 | 说明 |
|------|------|
| `wms_warehouses` | 仓库 |
| `wms_storage_zones` | 库区 |
| `wms_storage_locations` | 库位 |
| `wms_products` | 商品 |
| `wms_product_brand` | 商品品牌 |
| `wms_product_categories` | 商品分类 |
| `wms_product_images` | 商品图片 |
| `wms_cargo_owners` | 货主 |
| `wms_carrier` | 承运商 |
| `wms_packaging_material` | 包装材料 |
| `wms_stock_in_orders` | 入库单 |
| `wms_stock_in_order_items` | 入库单明细 |
| `wms_inventory` | 库存 |
| `wms_inventory_trans` | 库存流水 |
| `wms_out_orders` | 出库单 |
| `wms_out_orders_items` | 出库单明细 |
| `wms_out_orders_allocation` | 出库分配记录 |
| `wms_wave_master` | 波次主表 |
| `wms_wave_sku_summary` | 波次 SKU 汇总 |
| `wms_wave_strategy` | 波次策略 |
| `wms_shipment` | 发货单 |
| `wms_shipment_detail` | 发货明细 |
| `wms_shortage_registration` | 缺货登记 |
| `wms_tasks` | 仓储任务 |
| `wms_tasks_records` | 任务执行记录 |
| `wms_products_batchnum` | 商品批次 |

### IoT 表（6 张）

| 表名 | 说明 |
|------|------|
| `wms_iot_device` | 设备注册 |
| `wms_iot_product` | 物模型产品 |
| `wms_iot_product_model` | 物模型属性定义 |
| `wms_iot_monitor_config` | 监控配置 |
| `wms_iot_alert_rule` | 告警规则 |
| `wms_iot_alert_log` | 告警日志 |

### AI RAG 表（5 张）

| 表名 | 说明 |
|------|------|
| `airag_app` | AI 应用 |
| `airag_flow` | AI 流程设计 |
| `airag_knowledge` | 知识库 |
| `airag_knowledge_doc` | 知识库文档 |
| `airag_model` | LLM 模型配置 |

### 系统表（60+ 张）
包含 `sys_*`（用户/角色/权限/日志/字典等）、`onl_*`（在线开发）、`jimu_*`（报表）、`qrtz_*`（定时任务）等。

---

## 快速启动

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+
- Node.js 18+
- （可选）PostgreSQL + pgvector、RabbitMQ、EMQX、InfluxDB

### 后端

```bash
# 1. 克隆项目
cd backend

# 2. 初始化数据库
# 执行 db/xingchenwms-20250912.sql 或 xingchenwms-20251114.sql
mysql -u root -p < db/xingchenwms-20251114.sql

# 3. 修改配置
# 编辑 jeecg-module-system/jeecg-system-start/src/main/resources/application-dev.yml
# 将 MySQL/Redis/PostgreSQL/RabbitMQ 等连接信息替换为自己的实际值

# 4. 启动主应用（端口 8080）
cd jeecg-module-system/jeecg-system-start
mvn spring-boot:run

# 5. （可选）启动 IoT 告警服务（端口 18082）
cd jeecg-module-iot/jeecg-iot-alert
mvn spring-boot:run

# 6. （可选）启动 IoT 设备模拟器（端口 18084）
cd jeecg-module-iot/jeecg-iot-simulate
mvn spring-boot:run
```

### 前端

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 访问 http://localhost:5173
```

### API 文档

启动后端后访问：
- Knife4j 文档：`http://localhost:8080/jeecg-boot/doc.html`
- 账号：`jeecg` / 密码：`jeecg1314`

---

## 架构亮点

### 设计模式应用

| 模式 | 应用场景 | 位置 |
|------|----------|------|
| **策略模式** | 库存变更（5 种策略）/ 发货拆包 / 波次拆分 | `wms.inventory` / `wms.shipment.strategy` / `wms.wave.strategy` |
| **责任链模式** | 波次策略链：多种策略依次匹配处理 | `wms.wave.strategy` |
| **工厂模式** | AI 模型创建（ChatClient 工厂） | `ChatConfiguration.java` |
| **观察者模式** | WebSocket 消息推送 | `message.websocket` / `iot.manage.websocket` |

### 关键算法

- **A\* 寻路**：拣货路径优化（`pickroute/service`）
- **贪心算法**：发货拆包（按 20kg 重量上限）
- **向量相似度**：余弦距离 + 欧氏距离（`VectorDistanceUtils`）
- **HNSW 索引**：pgvector 高效向量检索

### 分布式与高可用

- **Redisson 分布式锁**：防止库存超卖
- **Quartz 集群**：定时任务 JDBC 持久化 + 集群部署
- **Redis ChatMemory**：AI 对话历史持久化
- **RabbitMQ 解耦**：IoT 数据流异步处理

---

## 配置说明

项目配置文件中所有敏感信息（API Key、密码、Token、签名密钥等）均已替换为中文占位提示语，请在使用前替换为自己的真实值。

### 关键配置项

| 配置项 | 位置 | 说明 |
|--------|------|------|
| MySQL 数据源 | `application-dev.yml` → `spring.datasource.dynamic.datasource.master` | 主数据库连接 |
| Redis | `application-dev.yml` → `spring.data.redis` | 缓存/分布式锁 |
| AI API Key | `application-dev.yml` → `spring.ai.openai.api-key` | 阿里云 DashScope / DeepSeek |
| 向量数据库 | `application-dev.yml` → `jeecg.ai-rag.embed-store` | PostgreSQL + pgvector |
| RabbitMQ | `application-dev.yml` → `spring.rabbitmq` | 消息队列 |
| MQTT Broker | `jeecg-iot-simulate` → `device-infos.brokerUri` | EMQX 连接 |
| InfluxDB | `jeecg-iot-alert` → `influx` | 时序数据存储 |
| OSS 对象存储 | `application-dev.yml` → `jeecg.oss` | 文件上传 |
| 顺丰快递 | `application-dev.yml` → `wms.express_api` | 快递 API 对接 |

---

## 作者

张星辰

## License

MIT
