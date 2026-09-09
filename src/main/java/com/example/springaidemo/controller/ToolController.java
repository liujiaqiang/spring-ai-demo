package com.example.springaidemo.controller;

import com.example.springaidemo.tool.DemoTools;
import com.example.springaidemo.tool.ToolTraceHolder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Function Calling（工具调用）演示。
 *
 * <p>模型在回答过程中会自主决定是否调用工具，例如：
 * <ul>
 *   <li>「北京天气怎么样？」→ 调用 getWeather(city=北京)</li>
 *   <li>「现在几点？」       → 调用 getCurrentTime()</li>
 *   <li>「(12+8)*3 等于几」  → 调用 calculator(expression="(12+8)*3")</li>
 * </ul>
 * 返回体同时给出最终回答和本次实际触发的工具调用链，方便观察原理。
 */
@RestController
@RequestMapping("/tool")
public class ToolController {

    private final ChatClient chatClient;
    private final DemoTools tools;

    public ToolController(ChatClient chatClient, DemoTools tools) {
        this.chatClient = chatClient;
        this.tools = tools;
    }

    @GetMapping("/chat")
    public Map<String, Object> chat(@RequestParam String message) {
        ToolTraceHolder.begin();
        try {
            // 关键：.tools(tools) 把带 @Tool 注解的方法注册给模型；
            // 不使用对话记忆，所以无需传 conversationId。
            String answer = chatClient.prompt()
                    .user(message)
                    .tools(tools)
                    // 全局注册了 MessageChatMemoryAdvisor，必须提供会话 id；
                    // 工具演示本身不依赖记忆，给个固定 id 即可。
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "tool-demo"))
                    .call()
                    .content();

            List<Map<String, Object>> calls = ToolTraceHolder.snapshot();
            return Map.of(
                    "message", message,
                    "answer", answer == null ? "" : answer,
                    "toolCalls", calls);
        } finally {
            ToolTraceHolder.clear();
        }
    }
}
