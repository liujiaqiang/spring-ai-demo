# Spring AI 快速入门 Demo

基于 **JDK 25 + Spring Boot 4.1 + Spring AI 2.0（GA）** 的最小可运行示例，演示：

- 💬 普通多轮对话（`ChatClient` + 会话记忆 `ChatMemory`）
- 🌊 流式输出（SSE，逐字返回）
- 🧱 结构化输出（模型 JSON 自动映射为 Java `record`）
- 🔌 OpenAI 兼容协议：一套代码接入 **OpenAI / DeepSeek / 火山方舟 / 智谱 / Moonshot** 等

## 环境要求

- JDK 25（本机已装 Temurin 25：`/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home`）
- Maven 3.9+（项目自带 Maven Wrapper，也可用 IntelliJ 内置 Maven）

## 1. 配置 API Key（三选一）

Spring AI 的 OpenAI starter 走 **OpenAI 兼容协议**，用环境变量切换厂商：

### OpenAI
```bash
export OPENAI_API_KEY=sk-xxxx
# base-url 默认 https://api.openai.com，模型默认 gpt-4o-mini
```

### DeepSeek（推荐，便宜好用）
```bash
export OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxx
export OPENAI_BASE_URL=https://api.deepseek.com
export OPENAI_MODEL=deepseek-chat
```

### 火山方舟（豆包 / GLM，本 Demo 默认已配好）
本项目默认就是**火山方舟「Agent Plan」套餐**的实测可用配置。

> ⚠️ **Spring AI 2.x 配置规则和 1.x 不同**：2.x 改用官方 OpenAI Java SDK，
> `base-url` 必须填**完整版本前缀**，SDK 会自动在末尾追加 `/chat/completions`；
> 1.x 的 `spring.ai.openai.chat.completions-path` 在 2.x **已移除**，配了也不生效。

```yaml
spring:
  ai:
    openai:
      api-key: ark-xxxx                          # 套餐 Key
      base-url: https://ark.cn-beijing.volces.com/api/plan/v3   # ⬅ 含版本段，SDK 自动拼 /chat/completions
      chat:
        options:
          model: glm-5.3                          # 实测可用；也可用 doubao-seed-2-0-pro-260215 等
```

不同套餐/端点只改 `base-url`（都要带版本段）和 Key：

| 场景 | base-url | 说明 |
|---|---|---|
| **Agent Plan**（本项目） | `https://ark.cn-beijing.volces.com/api/plan/v3` | 用 Agent Plan 套餐 Key，模型用 `glm-5.3` / `doubao-seed-2-0-pro-260215` 等 |
| **Coding Plan** | `https://ark.cn-beijing.volces.com/api/coding/v3` | 用 Coding Plan Key |
| **标准数据面** | `https://ark.cn-beijing.volces.com/api/v3` | 用平台 Key + 已在控制台开通的模型，或推理接入点 `ep-xxx` |

> 注意：Agent/Coding 套餐 Key 不能用于标准 `/api/v3` 端点（会 401）；标准平台 Key 调套餐端点里未授权的模型也会报错。模型名以控制台/套餐支持列表为准（`auto` 别名在 OpenAI 兼容端点不可用）。

### 火山方舟 · 其它常见厂商（可忽略）
DeepSeek：
```bash
export OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxx
export OPENAI_BASE_URL=https://api.deepseek.com/v1   # 2.x 需带 /v1，SDK 再拼 /chat/completions
export OPENAI_MODEL=deepseek-chat
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

## 3. 测试接口

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

浏览器直接访问 `http://localhost:8080/` 可看到四个标签页的可视化演示。

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
├── SpringAiDemoApplication.java   # 启动类
├── config/ChatConfig.java         # ChatClient + 对话记忆配置
├── controller/
│   ├── ChatController.java        # 对话/流式/结构化输出
│   └── ToolController.java        # 工具调用（Function Calling）
├── tool/
│   ├── DemoTools.java             # @Tool 工具：天气(真实API)/时间/计算器
│   └── ToolTraceHolder.java       # 记录本次调用的工具链（ThreadLocal）
├── weather/
│   └── WeatherService.java        # Open-Meteo 真实天气（geocoding + forecast）
└── dto/ActorFilms.java            # 结构化输出记录类型
src/main/resources/
├── application.yml                # 模型/Key/地址配置
└── static/index.html              # 四标签页演示界面
```

## 常见问题

- **401 / 鉴权失败**：检查 `OPENAI_API_KEY` 是否正确、厂商是否匹配。
- **404 Not Found**：base-url 不对。Spring AI 2.x 要求 base-url 含完整版本段（如方舟 `/api/plan/v3`、DeepSeek `/v1`），SDK 自动追加 `/chat/completions`；不要再用 1.x 的 `completions-path`。
- **模型名报错**：`OPENAI_MODEL` 要填对应厂商真实存在的模型/接入点 ID。
- **重启后记忆丢失**：示例用内存版 `MessageWindowChatMemory`；需要持久化可换成 `JdbcChatMemory`。
