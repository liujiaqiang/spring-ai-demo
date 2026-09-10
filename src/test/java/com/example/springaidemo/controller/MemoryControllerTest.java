package com.example.springaidemo.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link MemoryController} 的 Web 切片测试。
 *
 * <p>使用真实的 {@link InMemoryChatMemoryRepository} + {@link MessageWindowChatMemory}
 * （不 mock），这样端到端覆盖了 Controller → ChatMemory → Repository 整条链路，
 * 每个用例前清空所有会话，保证相互独立。
 */
@WebMvcTest(MemoryController.class)
@Import(MemoryControllerTest.MemoryTestConfig.class)
class MemoryControllerTest {

    @TestConfiguration
    static class MemoryTestConfig {

        @Bean
        ChatMemoryRepository chatMemoryRepository() {
            return new InMemoryChatMemoryRepository();
        }

        @Bean
        ChatMemory chatMemory(ChatMemoryRepository repository,
                              @Value("${spring.ai.chat.memory.max-messages:20}") int maxMessages) {
            return MessageWindowChatMemory.builder()
                    .chatMemoryRepository(repository)
                    .maxMessages(maxMessages)
                    .build();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ChatMemory chatMemory;

    @Autowired
    ChatMemoryRepository repository;

    @BeforeEach
    void cleanConversations() {
        repository.findConversationIds().forEach(repository::deleteByConversationId);
    }

    @Nested
    @DisplayName("GET /memory/conversations")
    class ConversationsEndpoint {

        @Test
        void emptyWhenNoConversation() throws Exception {
            mockMvc.perform(get("/memory/conversations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0))
                    // 窗口上限随配置下发，供前端展示
                    .andExpect(jsonPath("$.maxMessages").value(20))
                    .andExpect(jsonPath("$.conversations", hasSize(0)));
        }

        @Test
        void listsConversationsWithMessageCounts() throws Exception {
            chatMemory.add("u1", new UserMessage("你好"));
            chatMemory.add("u1", new AssistantMessage("你好，有什么可以帮你？"));
            chatMemory.add("u2", new UserMessage("另一个会话"));

            mockMvc.perform(get("/memory/conversations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(2))
                    // 不依赖底层 Map 的顺序：分别断言两个会话都在
                    .andExpect(jsonPath("$.conversations[?(@.conversationId=='u1')].messageCount")
                            .value(org.hamcrest.Matchers.contains(2)))
                    .andExpect(jsonPath("$.conversations[?(@.conversationId=='u2')].messageCount")
                            .value(org.hamcrest.Matchers.contains(1)));
        }
    }

    @Nested
    @DisplayName("GET /memory/messages")
    class MessagesEndpoint {

        @Test
        void returnsMessagesInChronologicalOrderWithTypeAndText() throws Exception {
            chatMemory.add("u1", new UserMessage("我叫张三"));
            chatMemory.add("u1", new AssistantMessage("你好，张三！"));

            mockMvc.perform(get("/memory/messages").param("conversationId", "u1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conversationId").value("u1"))
                    .andExpect(jsonPath("$.count").value(2))
                    .andExpect(jsonPath("$.maxMessages").value(20))
                    .andExpect(jsonPath("$.messages[0].type").value("user"))
                    .andExpect(jsonPath("$.messages[0].text").value("我叫张三"))
                    .andExpect(jsonPath("$.messages[1].type").value("assistant"))
                    .andExpect(jsonPath("$.messages[1].text").value("你好，张三！"));
        }

        @Test
        void returnsEmptyListForUnknownConversation() throws Exception {
            mockMvc.perform(get("/memory/messages").param("conversationId", "never-existed"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(0))
                    .andExpect(jsonPath("$.messages", hasSize(0)));
        }

        @Test
        void rejectsMissingConversationId() throws Exception {
            mockMvc.perform(get("/memory/messages"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /memory/clear")
    class ClearEndpoint {

        @Test
        void clearsSpecifiedConversationOnly() throws Exception {
            chatMemory.add("u1", new UserMessage("要删掉的"));
            chatMemory.add("u2", new UserMessage("保留的"));

            mockMvc.perform(delete("/memory/clear").param("conversationId", "u1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cleared").value(true))
                    .andExpect(jsonPath("$.conversationId").value("u1"));

            assertThatConversationEmpty("u1");
            // 其它会话不受影响
            org.assertj.core.api.Assertions.assertThat(chatMemory.get("u2")).hasSize(1);
        }

        private void assertThatConversationEmpty(String conversationId) throws Exception {
            mockMvc.perform(get("/memory/messages").param("conversationId", conversationId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(0));
        }
    }
}
