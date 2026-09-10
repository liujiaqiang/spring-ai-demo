package com.example.springaidemo.controller;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 对话记忆调试接口：直接观察内存版 ChatMemory 中各会话存了什么。
 *
 * <ul>
 *   <li>{@code GET    /memory/conversations}        —— 列出所有会话 id 及消息数</li>
 *   <li>{@code GET    /memory/messages?conversationId=u1} —— 查看某会话的全部消息</li>
 *   <li>{@code DELETE /memory/clear?conversationId=u1}    —— 清空某会话的记忆</li>
 * </ul>
 *
 * <p>注意：仅适用于演示用的 {@link org.springframework.ai.chat.memory.InMemoryChatMemoryRepository}，
 * 换成 JDBC 等持久化实现后这些接口依然可用（数据来自同一套 ChatMemory 抽象）。
 */
@RestController
@RequestMapping("/memory")
public class MemoryController {

    private final ChatMemory chatMemory;
    private final ChatMemoryRepository chatMemoryRepository;
    private final int maxMessages;

    public MemoryController(ChatMemory chatMemory, ChatMemoryRepository chatMemoryRepository,
                            @Value("${spring.ai.chat.memory.max-messages:20}") int maxMessages) {
        this.chatMemory = chatMemory;
        this.chatMemoryRepository = chatMemoryRepository;
        this.maxMessages = maxMessages;
    }

    /** 列出当前内存中所有会话及其消息条数。 */
    @GetMapping("/conversations")
    public Map<String, Object> conversations() {
        List<ConversationSummary> conversations = chatMemoryRepository.findConversationIds()
                .stream()
                .map(id -> new ConversationSummary(
                        id,
                        chatMemoryRepository.findByConversationId(id).size()))
                .toList();
        return Map.of(
                "total", conversations.size(),
                "maxMessages", maxMessages,
                "conversations", conversations);
    }

    /** 查看某个会话记忆里的完整消息列表（按时间顺序）。 */
    @GetMapping("/messages")
    public Map<String, Object> messages(@RequestParam String conversationId) {
        List<MessageView> messages = chatMemory.get(conversationId)
                .stream()
                .map(m -> new MessageView(m.getMessageType().getValue(), m.getText()))
                .toList();
        return Map.of(
                "conversationId", conversationId,
                "count", messages.size(),
                "maxMessages", maxMessages,
                "messages", messages);
    }

    /** 清空指定会话的记忆。 */
    @DeleteMapping("/clear")
    public Map<String, Object> clear(@RequestParam String conversationId) {
        chatMemory.clear(conversationId);
        return Map.of("conversationId", conversationId, "cleared", true);
    }

    /** 会话摘要。 */
    public record ConversationSummary(String conversationId, int messageCount) {
    }

    /** 单条消息视图：类型（user/assistant/system/tool）+ 文本内容。 */
    public record MessageView(String type, String text) {
    }
}
