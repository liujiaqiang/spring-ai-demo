package com.example.springaidemo.weather;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.client.MockRestServiceServer;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link WeatherService} 测试：
 * <ul>
 *   <li>用 {@link MockRestServiceServer} 打桩 Open-Meteo 的 geocoding / forecast 两步 HTTP 调用，
 *       不发起真实网络请求；</li>
 *   <li>WMO 天气码与风向的纯函数分支用参数化测试完整覆盖。</li>
 * </ul>
 */
class WeatherServiceTest {

    private MockRestServiceServer server;
    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        weatherService = new WeatherService(builder.build());
    }

    private static String geoJson(String name, String country, String admin1,
                                  double lat, double lon) {
        return """
                {"results":[{"name":"%s","latitude":%s,"longitude":%s,"country":"%s","admin1":"%s"}]}
                """.formatted(name, lat, lon, country, admin1);
    }

    private static String forecastJson(double temp, int humidity, double apparent,
                                       int code, double windSpeed, double windDir, String time) {
        return """
                {"current":{"time":"%s","temperature_2m":%s,"relative_humidity_2m":%s,
                "apparent_temperature":%s,"weather_code":%s,
                "wind_speed_10m":%s,"wind_direction_10m":%s}}
                """.formatted(time, temp, humidity, apparent, code, windSpeed, windDir);
    }

    @Nested
    @DisplayName("currentWeather 两步调用")
    class CurrentWeather {

        @Test
        void chainsGeocodingAndForecastAndFormatsChineseResult() {
            server.expect(requestTo(startsWith("https://geocoding-api.open-meteo.com/v1/search")))
                    // queryParam 在 URI 解码前匹配，中文需用百分号编码
                    .andExpect(queryParam("name", URLEncoder.encode("北京", StandardCharsets.UTF_8)))
                    .andExpect(queryParam("count", "1"))
                    .andExpect(queryParam("language", "zh"))
                    .andExpect(queryParam("format", "json"))
                    .andRespond(withSuccess(
                            geoJson("北京", "中国", "北京市", 39.9075, 116.39723),
                            org.springframework.http.MediaType.APPLICATION_JSON));

            server.expect(requestTo(startsWith("https://api.open-meteo.com/v1/forecast")))
                    .andExpect(queryParam("latitude", "39.9075"))
                    .andExpect(queryParam("longitude", "116.39723"))
                    .andExpect(queryParam("current",
                            "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,"
                                    + "wind_speed_10m,wind_direction_10m"))
                    .andExpect(queryParam("timezone", "Asia/Shanghai"))
                    .andRespond(withSuccess(
                            forecastJson(25.3, 40, 26.1, 0, 12.5, 90, "2026-09-09T15:00"),
                            org.springframework.http.MediaType.APPLICATION_JSON));

            String result = weatherService.currentWeather("北京");

            assertThat(result).isEqualTo(
                    "北京（中国·北京市） 当前：晴，气温 25.3℃（体感 26.1℃），相对湿度 40%，"
                            + "东风 12.5 km/h。观测时间 2026-09-09T15:00（数据：Open-Meteo）");
            server.verify();
        }

        @Test
        void omitsRegionSuffixWhenAdmin1IsBlankOrEqualsCityName() {
            server.expect(requestTo(startsWith("https://geocoding-api.open-meteo.com/")))
                    .andRespond(withSuccess(
                            geoJson("北京", "中国", "北京", 39.9, 116.4),
                            org.springframework.http.MediaType.APPLICATION_JSON));
            server.expect(requestTo(startsWith("https://api.open-meteo.com/")))
                    .andRespond(withSuccess(
                            forecastJson(20, 50, 19, 2, 3, 0, "2026-09-09T15:00"),
                            org.springframework.http.MediaType.APPLICATION_JSON));

            String result = weatherService.currentWeather("北京");

            assertThat(result).startsWith("北京 当前：多云");
        }

        @Test
        void throwsWhenCityNotFound() {
            server.expect(requestTo(startsWith("https://geocoding-api.open-meteo.com/")))
                    .andRespond(withSuccess("{\"results\":[]}",
                            org.springframework.http.MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> weatherService.currentWeather("火星城市"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("未找到城市：火星城市");
        }

        @Test
        void throwsWhenGeocodingReturnsNoResultsField() {
            server.expect(requestTo(startsWith("https://geocoding-api.open-meteo.com/")))
                    .andRespond(withSuccess("{}",
                            org.springframework.http.MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> weatherService.currentWeather("北京"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("未找到城市");
        }

        @Test
        void throwsWhenForecastHasNoCurrentData() {
            server.expect(requestTo(startsWith("https://geocoding-api.open-meteo.com/")))
                    .andRespond(withSuccess(
                            geoJson("北京", "中国", "北京市", 39.9, 116.4),
                            org.springframework.http.MediaType.APPLICATION_JSON));
            server.expect(requestTo(startsWith("https://api.open-meteo.com/")))
                    .andRespond(withSuccess("{\"current\":null}",
                            org.springframework.http.MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> weatherService.currentWeather("北京"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("天气服务未返回数据");
        }

        @Test
        void throwsWhenForecastBodyHasNoCurrentField() {
            server.expect(requestTo(startsWith("https://geocoding-api.open-meteo.com/")))
                    .andRespond(withSuccess(
                            geoJson("北京", "中国", "北京市", 39.9, 116.4),
                            org.springframework.http.MediaType.APPLICATION_JSON));
            server.expect(requestTo(startsWith("https://api.open-meteo.com/")))
                    .andRespond(withSuccess("{}",
                            org.springframework.http.MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> weatherService.currentWeather("北京"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("天气服务未返回数据");
        }
    }

    @ParameterizedTest(name = "WMO code {0} → {1}")
    @CsvSource(delimiterString = "->", textBlock = """
            0  -> 晴
            1  -> 大部晴朗
            2  -> 多云
            3  -> 阴
            45 -> 有雾
            48 -> 有雾
            51 -> 毛毛雨
            53 -> 毛毛雨
            55 -> 毛毛雨
            56 -> 冻毛毛雨
            57 -> 冻毛毛雨
            61 -> 小雨
            63 -> 中雨
            65 -> 大雨
            66 -> 冻雨
            67 -> 冻雨
            71 -> 小雪
            73 -> 中雪
            75 -> 大雪
            77 -> 米雪
            80 -> 小阵雨
            81 -> 阵雨
            82 -> 强阵雨
            85 -> 阵雪
            86 -> 阵雪
            95 -> 雷暴
            96 -> 雷暴伴冰雹
            99 -> 雷暴伴冰雹
            """)
    void describesAllKnownWmoWeatherCodes(int code, String expected) {
        assertThat(WeatherService.describeWeather(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "未知 WMO code {0}")
    @ValueSource(ints = {-1, 100, 999})
    void fallsBackToUnknownForUnmappedCode(int code) {
        assertThat(WeatherService.describeWeather(code)).isEqualTo("未知天气（代码 " + code + "）");
    }

    @ParameterizedTest(name = "风向 {0}° → {1}")
    @CsvSource(delimiterString = "->", textBlock = """
            0     -> 北
            22.4  -> 北
            22.5  -> 东北
            45    -> 东北
            90    -> 东
            135   -> 东南
            180   -> 南
            225   -> 西南
            270   -> 西
            315   -> 西北
            337.5 -> 北
            360   -> 北
            405   -> 东北
            """)
    void mapsWindDirectionToEightOctants(double deg, String expected) {
        assertThat(WeatherService.windDirection(deg)).isEqualTo(expected);
    }
}
