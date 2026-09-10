package com.example.springaidemo;

import com.example.springaidemo.controller.ChatController;
import com.example.springaidemo.controller.MemoryController;
import com.example.springaidemo.controller.ToolController;
import com.example.springaidemo.tool.DemoTools;
import com.example.springaidemo.weather.WeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot 上下文冒烟测试：验证全部 Bean（含 Spring AI 自动装配的 ChatClient）
 * 能在不发起真实网络调用的前提下成功装配。
 *
 * <p>测试环境使用内嵌 H2，ChatMemoryRepository 应被自动装配为
 * {@link JdbcChatMemoryRepository}，本类同时验证 JDBC 存取链路。
 */
@SpringBootTest
class SpringAiDemoApplicationTests {

    @Autowired
    ApplicationContext context;

    @Autowired
    ChatMemoryRepository chatMemoryRepository;

    @Autowired
    ChatMemory chatMemory;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    void coreBeansAreWired() {
        assertThat(context.getBean(ChatClient.class)).isNotNull();
        assertThat(context.getBean(ChatController.class)).isNotNull();
        assertThat(context.getBean(ToolController.class)).isNotNull();
        assertThat(context.getBean(MemoryController.class)).isNotNull();
        assertThat(context.getBean(ChatMemoryRepository.class)).isNotNull();
        assertThat(context.getBean(DemoTools.class)).isNotNull();
        assertThat(context.getBean(WeatherService.class)).isNotNull();
    }

    @Test
    void chatMemoryRepositoryIsJdbcBacked() {
        assertThat(chatMemoryRepository).isInstanceOf(JdbcChatMemoryRepository.class);
    }

    @Test
    void jdbcMemoryPersistsMessagesInChronologicalOrder() {
        String cid = "jdbc-it-" + System.nanoTime();
        try {
            chatMemory.add(cid, List.of(new UserMessage("我叫李四"), new AssistantMessage("你好，李四！")));

            List<Message> loaded = chatMemory.get(cid);
            assertThat(loaded).hasSize(2);
            assertThat(loaded.get(0).getMessageType().getValue()).isEqualTo("user");
            assertThat(loaded.get(0).getText()).isEqualTo("我叫李四");
            assertThat(loaded.get(1).getMessageType().getValue()).isEqualTo("assistant");
            assertThat(loaded.get(1).getText()).isEqualTo("你好，李四！");
            assertThat(chatMemoryRepository.findConversationIds()).contains(cid);

            chatMemory.clear(cid);
            assertThat(chatMemory.get(cid)).isEmpty();
        } finally {
            chatMemory.clear(cid);
        }
    }
}
