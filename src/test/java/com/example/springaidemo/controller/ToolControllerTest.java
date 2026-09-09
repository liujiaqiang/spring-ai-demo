package com.example.springaidemo.controller;

import com.example.springaidemo.controller.ChatClientFixtures.Stub;
import com.example.springaidemo.tool.DemoTools;
import com.example.springaidemo.tool.ToolTraceHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ToolController} 的 Web 切片测试，重点覆盖：
 * <ul>
 *   <li>请求过程中 {@link ToolTraceHolder} 记录的工具调用链会放进响应体；</li>
 *   <li>请求结束后 ThreadLocal 被清理；</li>
 *   <li>{@code .tools(tools)} 注册的是容器中的 {@link DemoTools} bean，会话 id 固定为 tool-demo。</li>
 * </ul>
 */
@WebMvcTest(ToolController.class)
@Import(ToolControllerTest.TestConfig.class)
class ToolControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        Stub chatClientStub() {
            return new Stub();
        }

        @Bean
        ChatClient chatClient(Stub stub) {
            return ChatClientFixtures.newChatClient(stub);
        }

        @Bean
        DemoTools demoTools() {
            return mock(DemoTools.class);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    Stub chatClient;

    @Autowired
    DemoTools demoTools;

    @BeforeEach
    void reset() {
        chatClient.reset();
    }

    @Test
    void returnsAnswerWithToolCallsCapturedDuringRequest() throws Exception {
        chatClient.contentAnswer = "答案是 60";
        // content() 在 controller 线程内、ToolTraceHolder.begin() 之后被调用，
        // 借此模拟模型回答期间框架执行了一次工具调用
        chatClient.onContentAnswer = () -> ToolTraceHolder.record("calculator",
                Map.of("expression", "(12+8)*3"), "60");

        mockMvc.perform(get("/tool/chat").param("message", "(12+8)*3 等于几"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("(12+8)*3 等于几"))
                .andExpect(jsonPath("$.answer").value("答案是 60"))
                .andExpect(jsonPath("$.toolCalls[0].tool").value("calculator"))
                .andExpect(jsonPath("$.toolCalls[0].arguments.expression").value("(12+8)*3"))
                .andExpect(jsonPath("$.toolCalls[0].result").value("60"));

        // 请求结束（回到测试线程后）ThreadLocal 应已被 finally 清理
        assertThat(ToolTraceHolder.snapshot()).isEmpty();

        // .tools(tools) 传入的是容器中的 DemoTools，会话 id 固定
        assertThat(chatClient.last().toolsCalls).hasSize(1);
        assertThat(chatClient.last().toolsCalls.getFirst()).containsExactly(demoTools);
        assertThat(chatClient.allConversationIds(ChatMemory.CONVERSATION_ID))
                .containsExactly("tool-demo");
    }

    @Test
    void noToolCallsYieldsEmptyArrayAndNullAnswerBecomesEmptyString() throws Exception {
        chatClient.contentAnswer = null;

        mockMvc.perform(get("/tool/chat").param("message", "hi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("hi"))
                .andExpect(jsonPath("$.answer").value(""))
                .andExpect(jsonPath("$.toolCalls").isArray())
                .andExpect(jsonPath("$.toolCalls").isEmpty());

        assertThat(ToolTraceHolder.snapshot()).isEmpty();
        assertThat(chatClient.last().toolsCalls.getFirst()).containsExactly(demoTools);
    }

    @Test
    void requiresMessageParameter() throws Exception {
        mockMvc.perform(get("/tool/chat"))
                .andExpect(status().isBadRequest());
        assertThat(chatClient.prompts).isEmpty();
    }
}
