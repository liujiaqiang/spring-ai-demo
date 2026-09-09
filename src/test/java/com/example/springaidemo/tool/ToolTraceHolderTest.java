package com.example.springaidemo.tool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolTraceHolder} 的纯单元测试，重点验证 ThreadLocal 生命周期语义。
 */
class ToolTraceHolderTest {

    @AfterEach
    void tearDown() {
        ToolTraceHolder.clear();
    }

    @Test
    void snapshotIsEmptyBeforeBegin() {
        ToolTraceHolder.clear();

        assertThat(ToolTraceHolder.snapshot()).isEmpty();
        // 未 begin 时 record 应被安全忽略，而不是抛 NPE
        ToolTraceHolder.record("any", null, null);

        assertThat(ToolTraceHolder.snapshot()).isEmpty();
    }

    @Test
    void beginRecordsEntriesInOrder() {
        ToolTraceHolder.begin();

        ToolTraceHolder.record("getWeather", Map.of("city", "北京"), "晴 25℃");
        ToolTraceHolder.record("calculator", Map.of("expression", "1+1"), "2");

        assertThat(ToolTraceHolder.snapshot())
                .extracting(e -> e.get("tool"))
                .containsExactly("getWeather", "calculator");
    }

    @Test
    void recordNormalizesNullArgumentsAndResultToEmptyString() {
        ToolTraceHolder.begin();

        ToolTraceHolder.record("noop", null, null);

        Map<String, Object> entry = ToolTraceHolder.snapshot().getFirst();
        assertThat(entry.get("tool")).isEqualTo("noop");
        assertThat(entry.get("arguments")).isEqualTo("");
        assertThat(entry.get("result")).isEqualTo("");
    }

    @Test
    void snapshotIsImmutableCopyAndIsolatedFromLaterRecords() {
        ToolTraceHolder.begin();
        ToolTraceHolder.record("a", "args-a", "res-a");

        var firstSnapshot = ToolTraceHolder.snapshot();
        ToolTraceHolder.record("b", "args-b", "res-b");

        // 快照是 copyOf，后续 record 不影响已取出的快照
        assertThat(firstSnapshot).hasSize(1);
        assertThat(ToolTraceHolder.snapshot()).hasSize(2);
    }

    @Test
    void clearRemovesTrace() {
        ToolTraceHolder.begin();
        ToolTraceHolder.record("a", "x", "y");

        ToolTraceHolder.clear();

        assertThat(ToolTraceHolder.snapshot()).isEmpty();
    }

    @Test
    void tracesAreIsolatedAcrossThreads() throws InterruptedException {
        ToolTraceHolder.begin();
        ToolTraceHolder.record("main", "m", "m");

        final var childResult = new java.util.concurrent.atomic.AtomicReference<java.util.List<Map<String, Object>>>();
        Thread child = new Thread(() -> {
            childResult.set(ToolTraceHolder.snapshot());
            ToolTraceHolder.begin();
            ToolTraceHolder.record("child", "c", "c");
            childResult.set(ToolTraceHolder.snapshot());
        });
        child.start();
        child.join();

        // 子线程初始看不到主线程的 trace
        // （join 前子线程会先 set 空列表再 set 自己的列表，取最终值校验其隔离性）
        assertThat(childResult.get()).hasSize(1);
        assertThat(childResult.get().getFirst().get("tool")).isEqualTo("child");
        // 主线程的 trace 不受子线程 begin/clear 影响
        assertThat(ToolTraceHolder.snapshot()).hasSize(1);
        assertThat(ToolTraceHolder.snapshot().getFirst().get("tool")).isEqualTo("main");
    }
}
