package com.example.springaidemo.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 与对话记忆配置。
 *
 * <p>{@link ChatClient.Builder} 由 spring-ai-starter-model-openai 自动装配，
 * 这里只做统一的默认系统提示词与多轮对话记忆装配。
 *
 * <p>{@link ChatMemoryRepository} 不在此显式声明，由 Spring AI 自动装配按 classpath 选择：
 * <ul>
 *   <li>引入 spring-ai-starter-model-chat-memory-repository-jdbc 且存在 DataSource（MySQL）时，
 *       自动使用 {@code JdbcChatMemoryRepository}，消息持久化到 SPRING_AI_CHAT_MEMORY 表；</li>
 *   <li>否则退回 {@code InMemoryChatMemoryRepository}（进程内存，重启清空）。</li>
 * </ul>
 */
@Configuration
public class ChatConfig {

    /**
     * 窗口记忆：每个会话保留最近 20 条消息；底层存储使用容器中自动装配的 ChatMemoryRepository
     * （MySQL 环境下是 JdbcChatMemoryRepository）。
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
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
