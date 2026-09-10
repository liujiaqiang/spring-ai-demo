package com.example.springaidemo.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ChatConfig} 的纯单元测试（不启动 Spring 容器）：
 * 校验记忆存储 Bean、消息窗口容量（20 条）以及 ChatClient 的默认系统提示词与记忆 Advisor 装配。
 */
class ChatConfigTest {

    private final ChatConfig config = new ChatConfig();

    private final ChatMemoryRepository repository = config.chatMemoryRepository();

    @Test
    void chatMemoryRepositoryIsInMemoryImpl() {
        assertThat(repository).isInstanceOf(InMemoryChatMemoryRepository.class);
        assertThat(config.chatMemoryRepository()).isNotSameAs(repository);
    }

    @Test
    void chatMemoryIsBackedByProvidedRepository() {
        // 同一个 repository 实例：ChatMemory 写入后，repository 应能直接读到（调试接口依赖这一点）
        ChatMemory memory = config.chatMemory(repository);
        memory.add("c1", new UserMessage("hello"));

        assertThat(repository.findByConversationId("c1"))
                .extracting(Message::getText)
                .containsExactly("hello");
    }

    @Test
    void chatMemoryKeepsAtMost20Messages() {
        ChatMemory memory = config.chatMemory(repository);

        assertThat(memory).isInstanceOf(MessageWindowChatMemory.class);

        List<Message> batch = IntStream.rangeClosed(1, 25)
                .<Message>mapToObj(i -> new UserMessage("m" + i))
                .toList();
        memory.add("c1", new ArrayList<>(batch));

        List<Message> retained = memory.get("c1");
        assertThat(retained).hasSize(20);
        // 窗口保留的是最近的 20 条
        assertThat(retained.getFirst().getText()).isEqualTo("m6");
        assertThat(retained.getLast().getText()).isEqualTo("m25");

        // 会话之间相互隔离
        assertThat(memory.get("other")).isEmpty();
    }

    @Test
    void chatClientConfiguresSystemPromptAndMemoryAdvisor() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_SELF);
        ChatClient built = mock(ChatClient.class);
        when(builder.build()).thenReturn(built);
        ChatMemory memory = config.chatMemory(repository);

        ChatClient client = config.chatClient(builder, memory);

        assertThat(client).isSameAs(built);
        verify(builder).defaultSystem("你是一个乐于助人的中文 AI 助手，回答简洁、准确、有条理。");

        ArgumentCaptor<Advisor> advisors = ArgumentCaptor.forClass(Advisor.class);
        verify(builder).defaultAdvisors(advisors.capture());
        assertThat(advisors.getAllValues())
                .hasSize(1)
                .hasOnlyElementsOfType(MessageChatMemoryAdvisor.class);
    }

    @Test
    void clearingMemoryRemovesConversation() {
        ChatMemory memory = config.chatMemory(repository);
        memory.add("c1", new ArrayList<>(List.of(new UserMessage("hello"), new AssistantMessage("hi"))));
        assertThat(memory.get("c1")).hasSize(2);

        memory.clear("c1");

        assertThat(memory.get("c1")).isEmpty();
        assertThat(repository.findConversationIds()).doesNotContain("c1");
    }
}
