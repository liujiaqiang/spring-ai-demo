package com.example.springaidemo.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 用 JDK 动态代理实现的 {@link ChatClient} 测试替身。
 *
 * <p>为什么不用 Mockito deep-stub：deep-stub 在 {@code tools(Object...)} 这类 varargs
 * 重载方法上会因「打桩用 matcher、真实调用用具体数组」解析出不同的下游 mock，
 * 挂在中间节点的 verify 会互相找不到。代理替身把整条 fluent 链的真实入参记录下来，
 * 断言直接、稳定，也无需实现接口的几十个方法。
 */
final class ChatClientFixtures {

    private ChatClientFixtures() {
    }

    /** 一次 {@code prompt()} 调用链上被捕获的状态。 */
    static final class Capture {
        final List<String> userMessages = new ArrayList<>();
        final List<Object[]> toolsCalls = new ArrayList<>();
        final List<Map<String, Object>> advisorParams = new ArrayList<>();
        boolean callUsed;
        boolean streamUsed;
        Class<?> entityType;

        List<String> conversationIds(String key) {
            return advisorParams.stream().map(p -> String.valueOf(p.get(key))).toList();
        }
    }

    /** 可配置的返回值 + 所有调用链捕获。 */
    static final class Stub {
        String contentAnswer = "";
        List<String> streamChunks = List.of();
        Object entity;
        /** content() 返回前在调用线程内执行的钩子（可用于模拟工具调用写 trace）。 */
        Runnable onContentAnswer = null;
        final List<Capture> prompts = new ArrayList<>();

        void reset() {
            contentAnswer = "";
            streamChunks = List.of();
            entity = null;
            onContentAnswer = null;
            prompts.clear();
        }

        Capture last() {
            return prompts.getLast();
        }

        List<String> allUserMessages() {
            return prompts.stream().flatMap(c -> c.userMessages.stream()).toList();
        }

        List<String> allConversationIds(String key) {
            return prompts.stream().flatMap(c -> c.conversationIds(key).stream()).toList();
        }

        List<Object> allTools() {
            List<Object> tools = new ArrayList<>();
            prompts.forEach(c -> c.toolsCalls.forEach(t -> tools.addAll(List.of(t))));
            return tools;
        }
    }

    static ChatClient newChatClient(Stub stub) {
        return (ChatClient) Proxy.newProxyInstance(
                ChatClient.class.getClassLoader(),
                new Class<?>[]{ChatClient.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "prompt" -> {
                            Capture capture = new Capture();
                            stub.prompts.add(capture);
                            return newRequestSpecProxy(stub, capture);
                        }
                        case "mutate" -> throw new UnsupportedOperationException();
                        case "toString" -> {
                            return "CapturingChatClient";
                        }
                        default -> {
                            return defaultValue(method.getReturnType());
                        }
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private static Object newRequestSpecProxy(Stub stub, Capture c) {
        return Proxy.newProxyInstance(
                ChatClient.ChatClientRequestSpec.class.getClassLoader(),
                new Class<?>[]{ChatClient.ChatClientRequestSpec.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "user" -> {
                            if (args != null && args.length == 1 && args[0] instanceof String s) {
                                c.userMessages.add(s);
                            }
                            return proxy;
                        }
                        case "tools" -> {
                            if (args != null && args.length == 1 && args[0] instanceof Object[] tools) {
                                c.toolsCalls.add(tools);
                            }
                            return proxy;
                        }
                        case "advisors" -> {
                            if (args != null && args.length == 1
                                    && args[0] instanceof Consumer<?> consumer) {
                                Map<String, Object> params = new HashMap<>();
                                ((Consumer<ChatClient.AdvisorSpec>) consumer)
                                        .accept(newAdvisorSpec(params));
                                c.advisorParams.add(params);
                            }
                            return proxy;
                        }
                        case "call" -> {
                            c.callUsed = true;
                            return newCallProxy(stub, c);
                        }
                        case "stream" -> {
                            c.streamUsed = true;
                            return newStreamProxy(stub);
                        }
                        case "toString" -> {
                            return "CapturingRequestSpec";
                        }
                        default -> {
                            return defaultValue(method.getReturnType());
                        }
                    }
                });
    }

    private static ChatClient.AdvisorSpec newAdvisorSpec(Map<String, Object> params) {
        return (ChatClient.AdvisorSpec) Proxy.newProxyInstance(
                ChatClient.AdvisorSpec.class.getClassLoader(),
                new Class<?>[]{ChatClient.AdvisorSpec.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "param" -> {
                        params.put((String) args[0], args[1]);
                        yield proxy;
                    }
                    case "params" -> {
                        params.putAll((Map<String, Object>) args[0]);
                        yield proxy;
                    }
                    case "toString" -> "CapturingAdvisorSpec";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object newCallProxy(Stub stub, Capture c) {
        return Proxy.newProxyInstance(
                ChatClient.CallResponseSpec.class.getClassLoader(),
                new Class<?>[]{ChatClient.CallResponseSpec.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "content" -> {
                        if (stub.onContentAnswer != null) {
                            stub.onContentAnswer.run();
                        }
                        yield stub.contentAnswer;
                    }
                    case "entity" -> {
                        if (args != null && args[0] instanceof Class<?> type) {
                            c.entityType = type;
                            yield stub.entity;
                        }
                        yield defaultValue(method.getReturnType());
                    }
                    case "chatResponse" -> (ChatResponse) defaultValue(ChatResponse.class);
                    case "toString" -> "CapturingCallSpec";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object newStreamProxy(Stub stub) {
        return Proxy.newProxyInstance(
                ChatClient.StreamResponseSpec.class.getClassLoader(),
                new Class<?>[]{ChatClient.StreamResponseSpec.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "content" -> Flux.fromIterable(stub.streamChunks);
                    case "toString" -> "CapturingStreamSpec";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type.isPrimitive()) {
            return 0;
        }
        return null;
    }
}
