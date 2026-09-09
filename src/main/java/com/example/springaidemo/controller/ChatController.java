package com.example.springaidemo.controller;

import com.example.springaidemo.dto.ActorFilms;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 对话接口演示。
 *
 * <ul>
 *   <li>{@code GET /chat}          —— 阻塞式多轮对话（带会话记忆）</li>
 *   <li>{@code GET /chat/stream}   —— 流式输出（SSE，逐字返回）</li>
 *   <li>{@code GET /chat/actor}    —— 结构化输出（自动映射为 Java 记录对象）</li>
 * </ul>
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 普通对话：同一个 conversationId 会记住上下文。
     * 例：/chat?message=我叫张三&conversationId=u1
     *     /chat?message=我叫什么名字？&conversationId=u1
     */
    @GetMapping
    public String chat(@RequestParam String message,
                       @RequestParam(defaultValue = "default") String conversationId) {
        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    /**
     * 流式对话：以 Server-Sent Events 逐段返回。
     * 例：/chat/stream?message=写一首关于春天的短诗&conversationId=u1
     */
    @GetMapping(value = "/stream", produces = "text/event-stream; charset=UTF-8")
    public Flux<String> stream(@RequestParam String message,
                               @RequestParam(defaultValue = "default") String conversationId,
                               jakarta.servlet.http.HttpServletResponse response) {
        // SSE 字节本身是 UTF-8；显式设置 charset，避免直接在浏览器地址栏打开时被按 GBK 解码而乱码。
        // （前端用 EventSource 消费时按规范始终以 UTF-8 解码，不受影响。）
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/event-stream;charset=UTF-8");
        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

    /**
     * 结构化输出：让模型返回符合 {@link ActorFilms} 结构的 JSON，Spring AI 自动反序列化。
     * 例：/chat/actor?actor=周星驰
     */
    @GetMapping("/actor")
    public ActorFilms actor(@RequestParam String actor) {
        return chatClient.prompt()
                .user("列出演员 " + actor + " 出演过的 5 部著名电影。")
                // 结构化输出不走对话记忆，给一个固定会话 id 即可（避免 advisor 报 conversationId null）
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "structured-output"))
                .call()
                .entity(ActorFilms.class);
    }
}
