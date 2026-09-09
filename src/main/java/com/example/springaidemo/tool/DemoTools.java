package com.example.springaidemo.tool;

import com.example.springaidemo.weather.WeatherService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 暴露给大模型的工具集合（Function Calling）。
 *
 * <p>方法上的 {@link Tool} 注解会被 Spring AI 转成模型可理解的 JSON Schema，
 * 模型在需要时会返回 tool_call，由框架自动调用对应 Java 方法，再把结果交回模型继续回答。
 */
@Component
public class DemoTools {

    private final WeatherService weatherService;

    public DemoTools(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @Tool(name = "getWeather", description = "查询指定城市当前的实时天气（真实数据，来源 Open-Meteo，支持中国城市），输入城市中文名")
    public String getWeather(
            @ToolParam(description = "城市中文名，例如：北京、上海、广州") String city) {
        String result;
        try {
            result = weatherService.currentWeather(city);
        } catch (Exception e) {
            // 工具返回友好的错误信息，模型可据此向用户说明
            result = "查询「" + city + "」天气失败：" + e.getMessage()
                    + "。可提示用户换一个城市名，或稍后再试。";
        }
        ToolTraceHolder.record("getWeather", Map.of("city", city == null ? "" : city), result);
        return result;
    }

    @Tool(name = "getCurrentTime", description = "获取当前的日期和时间，可指定时区")
    public String getCurrentTime(
            @ToolParam(description = "时区，可选，默认 Asia/Shanghai；如 UTC、America/New_York", required = false)
            String timezone) {
        String zone = (timezone == null || timezone.isBlank()) ? "Asia/Shanghai" : timezone;
        String now;
        try {
            now = LocalDateTime.now(ZoneId.of(zone))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " (" + zone + ")";
        } catch (Exception e) {
            now = "无法识别时区：" + zone + "，例如 Asia/Shanghai";
        }
        ToolTraceHolder.record("getCurrentTime", Map.of("timezone", zone), now);
        return now;
    }

    @Tool(name = "calculator", description = "计算一个数学表达式，支持加减乘除、括号、小数和负数，例如 (3+5)*2/4")
    public String calculator(
            @ToolParam(description = "数学表达式，例如：12*(3+4)-8/2") String expression) {
        String result;
        try {
            double v = new ExprParser(expression).parse();
            // 整数结果去掉小数点尾巴
            result = (v == Math.rint(v) && !Double.isInfinite(v))
                    ? String.valueOf((long) v)
                    : String.valueOf(v);
        } catch (Exception e) {
            result = "表达式无法计算：" + e.getMessage();
        }
        ToolTraceHolder.record("calculator",
                Map.of("expression", expression == null ? "" : expression), result);
        return result;
    }

    /**
     * 极简递归下降解析器：只做四则运算，不引入任何脚本引擎（避免任意代码执行）。
     * 文法：
     *   expr   := term (('+'|'-') term)*
     *   term   := factor (('*'|'/') factor)*
     *   factor := number | '(' expr ')' | ('+'|'-') factor
     */
    private static final class ExprParser {
        private final String s;
        private int pos = -1;
        private int ch;

        ExprParser(String s) {
            this.s = s == null ? "" : s;
        }

        double parse() {
            nextChar();
            double x = parseExpr();
            if (pos < s.length()) {
                throw new IllegalArgumentException("意外字符: " + (char) ch);
            }
            return x;
        }

        private void nextChar() {
            ch = (++pos < s.length()) ? s.charAt(pos) : -1;
        }

        private boolean eat(int c) {
            while (ch == ' ') {
                nextChar();
            }
            if (ch == c) {
                nextChar();
                return true;
            }
            return false;
        }

        private double parseExpr() {
            double x = parseTerm();
            while (true) {
                if (eat('+')) {
                    x += parseTerm();
                } else if (eat('-')) {
                    x -= parseTerm();
                } else {
                    return x;
                }
            }
        }

        private double parseTerm() {
            double x = parseFactor();
            while (true) {
                if (eat('*')) {
                    x *= parseFactor();
                } else if (eat('/')) {
                    x /= parseFactor();
                } else {
                    return x;
                }
            }
        }

        private double parseFactor() {
            if (eat('+')) {
                return parseFactor();
            }
            if (eat('-')) {
                return -parseFactor();
            }
            double x;
            int start = pos;
            if (eat('(')) {
                x = parseExpr();
                if (!eat(')')) {
                    throw new IllegalArgumentException("缺少右括号");
                }
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') {
                    nextChar();
                }
                x = Double.parseDouble(s.substring(start, pos));
            } else {
                throw new IllegalArgumentException("意外字符: " + (char) ch);
            }
            return x;
        }
    }
}
