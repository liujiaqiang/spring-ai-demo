package com.example.springaidemo.controller;

import com.example.springaidemo.controller.ChatClientFixtures.Stub;
import com.example.springaidemo.dto.ActorFilms;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ChatController} 的 Web 切片测试。
 *
 * <p>{@link ChatClient} fluent API 的替身由 {@link ChatClientFixtures} 用动态代理生成，
 * 它记录 user 消息、conversationId 等 advisor 参数，并按测试配置返回 content/entity/stream。
 */
@WebMvcTest(ChatController.class)
@Import(ChatControllerTest.TestConfig.class)
class ChatControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        Stub chatClientStub() {
            Stub stub = new Stub();
            return stub;
        }

        @Bean
        ChatClient chatClient(Stub stub) {
            return ChatClientFixtures.newChatClient(stub);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ChatClient injectedChatClient;

    @Autowired
    Stub chatClient;

    @BeforeEach
    void reset() {
        chatClient.reset();
    }

    @Nested
    @DisplayName("GET /chat")
    class ChatEndpoint {

        @Test
        void returnsModelAnswerAndPassesConversationId() throws Exception {
            chatClient.contentAnswer = "你好，张三！";

            mockMvc.perform(get("/chat").param("message", "我叫张三").param("conversationId", "u1"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("你好，张三！"));

            assertThat(chatClient.allUserMessages()).containsExactly("我叫张三");
            assertThat(chatClient.allConversationIds(ChatMemory.CONVERSATION_ID))
                    .containsExactly("u1");
            assertThat(chatClient.last().callUsed).isTrue();
        }

        @Test
        void usesDefaultConversationIdWhenAbsent() throws Exception {
            chatClient.contentAnswer = "收到";

            mockMvc.perform(get("/chat").param("message", "在吗"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("收到"));

            assertThat(chatClient.allConversationIds(ChatMemory.CONVERSATION_ID))
                    .containsExactly("default");
        }

        @Test
        void rejectsRequestWithoutRequiredMessage() throws Exception {
            mockMvc.perform(get("/chat"))
                    .andExpect(status().isBadRequest());
            assertThat(chatClient.prompts).isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /chat/actor")
    class ActorEndpoint {

        @Test
        void returnsStructuredActorFilmsEntity() throws Exception {
            ActorFilms actorFilms = new ActorFilms("周星驰",
                    List.of("功夫", "少林足球", "喜剧之王", "大话西游", "国产凌凌漆"));
            chatClient.entity = actorFilms;

            mockMvc.perform(get("/chat/actor").param("actor", "周星驰"))
                    .andExpect(status().isOk())
                    .andExpect(content().json(
                            """
                            {
                              "actor": "周星驰",
                              "films": ["功夫", "少林足球", "喜剧之王", "大话西游", "国产凌凌漆"]
                            }
                            """));

            assertThat(chatClient.allUserMessages())
                    .containsExactly("列出演员 周星驰 出演过的 5 部著名电影。");
            assertThat(chatClient.last().entityType).isEqualTo(ActorFilms.class);
            assertThat(chatClient.allConversationIds(ChatMemory.CONVERSATION_ID))
                    .containsExactly("structured-output");
        }
    }

    @Nested
    @DisplayName("GET /chat/stream")
    class StreamEndpoint {

        @Test
        void streamEndpointConfiguresUtf8SseAndStreamsFlux() {
            chatClient.streamChunks = List.of("春", "天", "来了");
            MockHttpServletResponse response = new MockHttpServletResponse();

            Flux<String> flux = new ChatController(injectedChatClient)
                    .stream("写一首诗", "u1", response);

            assertThat(flux.collectList().block()).containsExactly("春", "天", "来了");
            assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
            assertThat(response.getContentType()).contains("text/event-stream");
            assertThat(chatClient.prompts).hasSize(1);
            assertThat(chatClient.allUserMessages()).containsExactly("写一首诗");
            assertThat(chatClient.last().streamUsed).isTrue();
            assertThat(chatClient.allConversationIds(ChatMemory.CONVERSATION_ID))
                    .containsExactly("u1");
        }
    }
}
