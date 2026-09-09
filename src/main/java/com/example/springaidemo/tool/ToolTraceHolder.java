package com.example.springaidemo.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用 ThreadLocal 记录「这一次 HTTP 请求中模型实际调用了哪些工具」，
 * 方便在前端展示 Function Calling 的完整过程（工具名 / 入参 / 返回值）。
 *
 * <p>记录器在 Controller 里 begin，请求结束后读取并 clear。
 */
public final class ToolTraceHolder {

    private static final ThreadLocal<List<Map<String, Object>>> TRACE = new ThreadLocal<>();

    private ToolTraceHolder() {
    }

    public static void begin() {
        TRACE.set(new ArrayList<>());
    }

    public static void record(String tool, Object arguments, Object result) {
        List<Map<String, Object>> list = TRACE.get();
        if (list != null) {
            list.add(Map.of(
                    "tool", tool,
                    "arguments", arguments == null ? "" : arguments,
                    "result", result == null ? "" : result));
        }
    }

    public static List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> list = TRACE.get();
        return list == null ? List.of() : List.copyOf(list);
    }

    public static void clear() {
        TRACE.remove();
    }
}
