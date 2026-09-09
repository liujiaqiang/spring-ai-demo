package com.example.springaidemo.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 与对话记忆配置。
 *
 * <p>{@link ChatClient.Builder} 由 spring-ai-starter-model-openai 自动装配，
 * 这里只做统一的默认系统提示词与多轮对话记忆装配。
 */
@Configuration
public class ChatConfig {

    /**
     * 内存版对话记忆：每个会话保留最近 20 条消息（进程重启后清空）。
     * 如需持久化，可换成 JdbcChatMemory / RedisChatMemory。
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder
                .defaultSystem("你是一个乐于助人的中文 AI 助手，回答简洁、准确、有条理。")
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
