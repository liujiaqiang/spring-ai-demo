package com.example.springaidemo.tool;

import com.example.springaidemo.weather.WeatherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link DemoTools} 单元测试：三个 @Tool 方法的业务分支 + {@link ToolTraceHolder} 记录行为。
 * WeatherService 被 mock，不发起真实网络请求。
 */
class DemoToolsTest {

    private WeatherService weatherService;
    private DemoTools tools;

    @BeforeEach
    void setUp() {
        weatherService = mock(WeatherService.class);
        tools = new DemoTools(weatherService);
        ToolTraceHolder.begin();
    }

    @AfterEach
    void tearDown() {
        ToolTraceHolder.clear();
    }

    @Nested
    @DisplayName("calculator")
    class Calculator {

        @ParameterizedTest(name = "{0} = {1}")
        @CsvSource(delimiterString = "=>", textBlock = """
                1+2            => 3
                10-3-2         => 5
                4*5            => 20
                20/4           => 5
                (12+8)*3       => 60
                2+3*4          => 14
                (2+3)*4        => 20
                7/2            => 3.5
                -5+3           => -2
                +8-3           => 5
                10/(2+3)       => 2
                2.5 + 2.5      => 5
                3 * ( -2 + 4 ) => 6
                """)
        void evaluatesValidExpressions(String expression, String expected) {
            assertThat(tools.calculator(expression)).isEqualTo(expected);
        }

        @Test
        void returnsInfinityForDivisionByZero() {
            assertThat(tools.calculator("1/0")).isEqualTo("Infinity");
        }

        @ParameterizedTest(name = "非法表达式：{0}")
        @ValueSource(strings = {"1+", "(2+3", "2+3)", "abc", "1**2", "2..5", "", "   "})
        void returnsFriendlyErrorForInvalidExpressions(String expression) {
            assertThat(tools.calculator(expression)).startsWith("表达式无法计算：");
        }

        @Test
        void nullExpressionIsTreatedAsEmptyAndReportedAsError() {
            assertThat(tools.calculator(null)).startsWith("表达式无法计算：");
        }
    }

    @Nested
    @DisplayName("getCurrentTime")
    class CurrentTime {

        @Test
        void defaultsToAsiaShanghaiWhenTimezoneIsNull() {
            String result = tools.getCurrentTime(null);

            assertTimeMatches(result, "Asia/Shanghai");
        }

        @Test
        void defaultsToAsiaShanghaiWhenTimezoneIsBlank() {
            String result = tools.getCurrentTime("   ");

            assertTimeMatches(result, "Asia/Shanghai");
        }

        @Test
        void honorsProvidedTimezone() {
            String result = tools.getCurrentTime("UTC");

            assertTimeMatches(result, "UTC");
        }

        @Test
        void returnsFriendlyErrorForUnknownTimezone() {
            String result = tools.getCurrentTime("Not/AZone");

            assertThat(result).isEqualTo("无法识别时区：Not/AZone，例如 Asia/Shanghai");
        }

        private void assertTimeMatches(String result, String zone) {
            String expectedNow = LocalDateTime.now(ZoneId.of(zone))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            assertThat(result).endsWith("(" + zone + ")");
            // 去掉时区后缀后应与当前时间一致（格式化发生在同一秒内）
            String timePart = result.substring(0, "yyyy-MM-dd HH:mm:ss".length());
            assertThat(timePart).isEqualTo(expectedNow);
        }
    }

    @Nested
    @DisplayName("getWeather")
    class Weather {

        @Test
        void returnsServiceResultOnSuccess() {
            when(weatherService.currentWeather("北京")).thenReturn("北京 当前：晴，气温 25.0℃");

            String result = tools.getWeather("北京");

            assertThat(result).isEqualTo("北京 当前：晴，气温 25.0℃");
        }

        @Test
        void convertsServiceExceptionIntoFriendlyMessage() {
            when(weatherService.currentWeather("不存在的城市"))
                    .thenThrow(new IllegalArgumentException("未找到城市：不存在的城市"));

            String result = tools.getWeather("不存在的城市");

            assertThat(result)
                    .startsWith("查询「不存在的城市」天气失败：未找到城市：不存在的城市")
                    .contains("换一个城市名");
        }
    }

    @Test
    void everyToolCallIsRecordedInTheTraceHolder() {
        when(weatherService.currentWeather("上海")).thenReturn("上海 晴");

        tools.calculator("1+1");
        tools.getCurrentTime("UTC");
        tools.getWeather("上海");

        List<Map<String, Object>> trace = ToolTraceHolder.snapshot();
        assertThat(trace).hasSize(3);
        assertThat(trace).extracting(e -> e.get("tool"))
                .containsExactly("calculator", "getCurrentTime", "getWeather");
        assertThat(trace.getFirst().get("arguments")).isEqualTo(Map.of("expression", "1+1"));
        assertThat(trace.getFirst().get("result")).isEqualTo("2");
        assertThat(trace.get(2).get("arguments")).isEqualTo(Map.of("city", "上海"));
        assertThat(trace.get(2).get("result")).isEqualTo("上海 晴");
    }

    @Test
    void traceIsNotRecordedWhenHolderNotBegun() {
        ToolTraceHolder.clear();

        tools.calculator("1+1");

        assertThat(ToolTraceHolder.snapshot()).isEmpty();
    }
}
