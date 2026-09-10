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

import java.util.ArrayList;
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

    @Test
    void chatMemoryWindowHonoursConfiguredMaxMessages() {
        // 测试未设置 spring.ai.chat.memory.max-messages，应落到默认值 20。
        // 这条用例同时验证 @Value 默认值真的注入进了 MessageWindowChatMemory。
        String cid = "window-it-" + System.nanoTime();
        try {
            List<Message> batch = new ArrayList<>();
            for (int i = 1; i <= 25; i++) {
                batch.add(new UserMessage("m" + i));
            }
            chatMemory.add(cid, batch);

            List<Message> retained = chatMemory.get(cid);
            assertThat(retained).hasSize(20);
            assertThat(retained.getFirst().getText()).isEqualTo("m6");
            assertThat(retained.getLast().getText()).isEqualTo("m25");
            // 数据库里也只应该留下 20 行（写入时已裁剪，不是读取时过滤）
            assertThat(chatMemoryRepository.findByConversationId(cid)).hasSize(20);
        } finally {
            chatMemory.clear(cid);
        }
    }
}
