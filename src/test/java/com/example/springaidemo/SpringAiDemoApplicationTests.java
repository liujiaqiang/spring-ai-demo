package com.example.springaidemo;

import com.example.springaidemo.controller.ChatController;
import com.example.springaidemo.controller.MemoryController;
import com.example.springaidemo.controller.ToolController;
import com.example.springaidemo.tool.DemoTools;
import com.example.springaidemo.weather.WeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot 上下文冒烟测试：验证全部 Bean（含 Spring AI 自动装配的 ChatClient）
 * 能在不发起真实网络调用的前提下成功装配。
 */
@SpringBootTest
class SpringAiDemoApplicationTests {

    @Autowired
    ApplicationContext context;

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
}
