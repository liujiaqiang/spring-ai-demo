# Spring AI 快速入门 Demo

基于 **JDK 25 + Spring Boot 4.1 + Spring AI 2.0（GA）** 的最小可运行示例，演示：

- 💬 普通多轮对话（`ChatClient` + 会话记忆 `ChatMemory`）
- 🌊 流式输出（SSE，逐字返回）
- 🧱 结构化输出（模型 JSON 自动映射为 Java `record`）
- 🛠 工具调用 / Function Calling（天气、时间、计算器，模型自主决定调用）
- 🗄️ **对话记忆持久化到 MySQL**（`JdbcChatMemoryRepository`，重启不丢）
- 🧠 **可视化记忆调试台**（聊天 × 记忆内容实时对照）
- 🔌 OpenAI 兼容协议：一套代码接入 **OpenAI / DeepSeek / 火山方舟 / 智谱 / Moonshot** 等

## 环境要求

- JDK 25（本机已装 Temurin 25：`/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home`）
- Maven 3.9+（项目自带 Maven Wrapper，也可用 IntelliJ 内置 Maven）
- MySQL 5.7+ / 8.x（记忆持久化用；不配置数据库时可参考文末「不使用数据库」退化为内存版）

## 1. 配置 API Key 与数据库连接

敏感配置统一放在 **`src/main/resources/application-local.yml`**（已加入 `.gitignore`，不会提交）。
首次使用请复制示例文件并填写：

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

需要填两部分：

### 1.1 模型 API Key

Spring AI 的 OpenAI starter 走 **OpenAI 兼容协议**，用环境变量/配置切换厂商。本项目默认是**火山方舟「Agent Plan」套餐**的实测配置：

> ⚠️ **Spring AI 2.x 配置规则和 1.x 不同**：2.x 改用官方 OpenAI Java SDK，
> `base-url` 必须填**完整版本前缀**，SDK 会自动在末尾追加 `/chat/completions`；
> 1.x 的 `spring.ai.openai.chat.completions-path` 在 2.x **已移除**，配了也不生效。

```yaml
spring:
  ai:
    openai:
      api-key: ark-xxxx                                           # 套餐 Key
      base-url: https://ark.cn-beijing.volces.com/api/plan/v3    # ⬅ 含版本段
      chat:
        options:
          model: glm-5.3                                          # 也可用 doubao-seed-2-0-pro 等
```

不同套餐/端点只改 `base-url`（都要带版本段）和 Key：

| 场景 | base-url | 说明 |
|---|---|---|
| **Agent Plan**（本项目默认） | `https://ark.cn-beijing.volces.com/api/plan/v3` | 用 Agent Plan 套餐 Key |
| **Coding Plan** | `https://ark.cn-beijing.volces.com/api/coding/v3` | 用 Coding Plan Key |
| **标准数据面** | `https://ark.cn-beijing.volces.com/api/v3` | 用平台 Key + 已开通模型或接入点 `ep-xxx` |
| **DeepSeek** | `https://api.deepseek.com/v1` | Key 用 DeepSeek 的，模型填 `deepseek-chat` |
| **OpenAI** | `https://api.openai.com/v1` | Key 用 OpenAI 的 |

> 注意：套餐 Key 不能用于标准 `/api/v3` 端点（会 401）。模型名以控制台/套餐支持列表为准（`auto` 别名在 OpenAI 兼容端点不可用）。

### 1.2 MySQL 连接（记忆持久化）

先建库（**表不用手动建**，应用首次启动会自动执行框架内置的 `schema-mysql.sql`）：

```sql
CREATE DATABASE spring_ai_demo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然后在 `application-local.yml` 填写连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/spring_ai_demo?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: 你的用户名
    password: 你的密码
    driver-class-name: com.mysql.cj.jdbc.Driver
  ai:
    chat:
      memory:
        repository:
          jdbc:
            initialize-schema: always   # MySQL 是非内嵌库，必须显式开启自动建表
```

启动后自动创建的表（表名为框架固定，2.0.1 暂不支持改名）：

```sql
SPRING_AI_CHAT_MEMORY(
  conversation_id VARCHAR(36),                                  -- 会话 id
  content         TEXT,                                         -- 消息内容
  type            ENUM('USER','ASSISTANT','SYSTEM','TOOL'),     -- 消息类型
  timestamp       TIMESTAMP,
  sequence_id     BIGINT                                        -- 同会话内的消息顺序
)
```

## 2. 运行

```bash
# 方式一：Maven Wrapper（首次会自动下载 Maven）
./mvnw spring-boot:run

# 方式二：本机已装 Maven
mvn spring-boot:run

# 方式三：打包后运行
./mvnw clean package
java -jar target/spring-ai-demo-0.0.1-SNAPSHOT.jar
```

启动后服务监听 `http://localhost:8080`。

## 3. 页面

| 地址 | 内容 |
|---|---|
| http://localhost:8080/ | 功能首页：流式对话 / 普通对话 / 结构化输出 / 工具调用 四个标签页 |
| http://localhost:8080/memory.html | **🧠 聊天 × 记忆调试台**：左侧流式多轮对话，右侧实时查看 MySQL 中按 `conversationId` 存储的消息，支持切换/清空会话 |

调试台建议体验路径：先说「我叫张三」，再问「我叫什么名字？」，观察右栏如何随每轮对话增加 `user` / `assistant` 消息；点 `✕` 清空记忆后再问，模型即「失忆」。

## 4. HTTP 接口

```bash
# ① 普通对话（同一 conversationId 带记忆）
curl "http://localhost:8080/chat?message=我叫张三&conversationId=u1"
curl "http://localhost:8080/chat?message=我叫什么名字？&conversationId=u1"

# ② 流式输出（SSE）
curl -N "http://localhost:8080/chat/stream?message=写一首关于春天的短诗&conversationId=u1"

# ③ 结构化输出（返回 JSON：{"actor":"周星驰","films":[...]}）
curl "http://localhost:8080/chat/actor?actor=周星驰"

# ④ 工具调用（Function Calling）：模型自主决定调用哪个本地工具
#    返回 {answer, toolCalls:[{tool, arguments, result}...]}
curl -G "http://localhost:8080/tool/chat" --data-urlencode "message=北京天气怎么样？再帮我算 (12+8)*3"
```

记忆调试接口（`MemoryController`，页面与排查用）：

```bash
# 列出所有会话 id 及消息条数
curl "http://localhost:8080/memory/conversations"

# 查看某个会话的完整消息（按时间顺序，含 type 与 text）
curl "http://localhost:8080/memory/messages?conversationId=u1"

# 清空某个会话的记忆
curl -X DELETE "http://localhost:8080/memory/clear?conversationId=u1"
```

也可以直接查表验证持久化：

```sql
SELECT conversation_id, type, LEFT(content, 30) AS content, timestamp
FROM SPRING_AI_CHAT_MEMORY
WHERE conversation_id = 'u1'
ORDER BY sequence_id;
```

## 对话记忆是如何实现的

记忆能力分三层（这也是持久化时业务代码零改动的原因）：

```
MessageChatMemoryAdvisor   ← 拦截器：请求前把历史拼进 prompt，响应后存回复
        │
        ▼
MessageWindowChatMemory    ← 策略：每个会话最多保留 20 条，超限淘汰最旧消息（SystemMessage 保留）
        │
        ▼
ChatMemoryRepository       ← 存储：MySQL 环境用 JdbcChatMemoryRepository，否则用内存 Map
```

- **大模型本身无状态**，所谓"多轮对话"就是 Advisor 在每次调用前，按 `conversationId` 把历史消息从存储中读出、重新拼进 prompt；`conversationId` 只是存储的 key。
- `before()`：读取历史 → 拼到本次 prompt 前 → 先存入本轮用户消息；`after()`：模型返回后存入 assistant 回复。
- 流式场景下 token 照常实时推送，记忆在**整个流结束时聚合后只写入一次**。
- 窗口按**消息条数**（20 条，非 token 数）裁剪，且裁剪点对齐到 USER 消息，避免留下"没有对应提问的回复"。

## 工具调用（Function Calling）

`tool/DemoTools.java` 用 `@Tool` 注解把普通 Java 方法暴露给模型，模型会**自主判断**何时调用、传什么参数，框架执行后把结果交回模型继续作答：

| 工具方法 | 作用 | 触发示例 |
|---|---|---|
| `getWeather(city)` | **真实实时天气**（Open-Meteo，免费免 Key，支持中文城市） | 「北京天气怎么样？」 |
| `getCurrentTime(timezone)` | 查当前时间 | 「现在几点了？」 |
| `calculator(expression)` | 四则运算（自带安全解析器，不用脚本引擎） | 「(12+8)*3 等于多少」 |

核心代码（`controller/ToolController.java`）：
```java
chatClient.prompt()
          .user(message)
          .tools(new DemoTools())      // ⬅ 注册带 @Tool 方法的对象
          .call()
          .content();
```
- 给 `@Tool` / `@ToolParam` 写清楚中文描述很关键——模型靠这些描述决定「要不要调、怎么传参」。
- `ToolTraceHolder` 用 ThreadLocal 记录每次请求真实触发的工具链，仅用于前端展示原理。
- 想接真实能力（查数据库、调内部 HTTP API、发邮件…），照着 `DemoTools` 加一个 `@Tool` 方法即可。

## 代码结构

```
src/main/java/com/example/springaidemo/
├── SpringAiDemoApplication.java        # 启动类
├── config/ChatConfig.java              # ChatClient + 窗口记忆配置（存储由自动装配选择 JDBC/内存）
├── controller/
│   ├── ChatController.java             # 对话 / 流式 / 结构化输出
│   ├── ToolController.java             # 工具调用（Function Calling）
│   └── MemoryController.java           # 记忆调试接口（查看会话 / 消息 / 清空）
├── tool/
│   ├── DemoTools.java                  # @Tool 工具：天气(真实API)/时间/计算器
│   └── ToolTraceHolder.java            # 记录本次调用的工具链（ThreadLocal）
├── weather/
│   └── WeatherService.java             # Open-Meteo 真实天气（geocoding + forecast）
└── dto/ActorFilms.java                 # 结构化输出记录类型
src/main/resources/
├── application.yml                     # 公共配置（默认激活 local profile）
├── application-local.yml.example       # 本地配置模板（Key + 数据源，提交到 git）
├── application-local.yml               # 本地真实配置（gitignore，不提交）
└── static/
    ├── index.html                      # 四标签页演示界面
    └── memory.html                     # 聊天 × 记忆调试台
src/test/                               # 109 个测试（./mvnw test）
├── java/.../config/ChatConfigTest.java
├── java/.../controller/MemoryControllerTest.java   # 记忆接口切片测试
├── java/.../SpringAiDemoApplicationTests.java      # 含 JDBC 记忆存取往返集成测试
└── resources/application.yml           # 测试用内嵌 H2（MySQL 兼容模式），不依赖本地 MySQL
```

## 测试

```bash
./mvnw test
```

- 共 109 个测试：纯单元测试（工具、窗口策略）、Web 切片测试（MockMvc）、Spring 上下文集成测试。
- 测试环境使用**内嵌 H2**（`MODE=MySQL`）跑真实的 JDBC 记忆链路（自动建表 → 写入 → 查询 → 删除），因此无需启动本地 MySQL；生产运行时才连 MySQL。

## 常见问题

- **401 / 鉴权失败**：检查 API Key 是否正确、厂商与 base-url 是否匹配；套餐 Key 不能调标准端点。
- **404 Not Found**：base-url 不对。Spring AI 2.x 要求含完整版本段（方舟 `/api/plan/v3`、DeepSeek `/v1`），SDK 自动追加 `/chat/completions`；不要再用 1.x 的 `completions-path`。
- **模型名报错**：模型名要填对应厂商真实存在的模型 / 接入点 ID。
- **启动报数据库连接失败**：确认 MySQL 已启动、库已创建、`application-local.yml` 中用户名/密码/端口正确。MySQL 5.7 配合新驱动一般可用，如遇协议错误可锁定较低版本驱动或升级到 8.x。
- **表没有自动创建**：MySQL 必须显式配置 `spring.ai.chat.memory.repository.jdbc.initialize-schema: always`（内嵌库默认即建，非内嵌库默认不建）。
- **重启后记忆还在吗**：在。消息已持久化到 `SPRING_AI_CHAT_MEMORY` 表；但每个会话只保留最近 20 条消息（窗口策略）。要彻底重置可删表或调用 `/memory/clear`。
- **不使用数据库可以吗**：可以。删除/注释 `application-local.yml` 中的 `spring.datasource` 配置（并不加 JDBC 依赖），自动装配会退回 `InMemoryChatMemoryRepository`（进程内存，重启清空）。
