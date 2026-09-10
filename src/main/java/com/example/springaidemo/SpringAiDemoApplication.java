package com.example.springaidemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AI 快速入门 Demo 启动类。
 *
 * <p>使用前通过环境变量配置（OpenAI 兼容协议，DeepSeek / 火山方舟 / 智谱等通用）：
 * <pre>
 *   export OPENAI_API_KEY=你的Key
 *   # 可选：默认走 OpenAI；接其它厂商时覆盖下面两项
 *   # export SPRING_AI_OPENAI_BASE_URL=<a href="https://api.deepseek.com">...</a>
 *   # export SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL=deepseek-chat
 * </pre>
 */
@SpringBootApplication
public class SpringAiDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiDemoApplication.class, args);
    }
}
