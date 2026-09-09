package com.example.springaidemo.weather;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * 真实天气服务：基于 Open-Meteo（https://open-meteo.com/）。
 *
 * <p>优点：免费、无需 API Key、无需注册、支持中文城市名，适合 Demo。
 * 两步调用：
 * <ol>
 *   <li>geocoding 接口：城市中文名 → 经纬度</li>
 *   <li>forecast 接口：经纬度 → 实时天气</li>
 * </ol>
 */
@Component
public class WeatherService {

    private final RestClient http;

    public WeatherService() {
        // 用 JDK 内置 HttpClient（SimpleClientHttpRequestFactory 底层 HttpURLConnection
        // 对中文查询参数处理有问题，会导致 geocoding 查不到城市）。
        HttpClient jdkClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkClient);
        factory.setReadTimeout(Duration.ofSeconds(12));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** 测试专用构造器：允许注入自定义 {@link RestClient}（例如绑定 MockRestServiceServer）。 */
    WeatherService(RestClient http) {
        this.http = http;
    }

    /** 查询某城市当前天气，返回一段中文描述。城市找不到时抛 {@link IllegalArgumentException}。 */
    public String currentWeather(String city) {
        Geo geo = geocode(city);

        URI url = UriComponentsBuilder.fromUriString("https://api.open-meteo.com/v1/forecast")
                .queryParam("latitude", geo.latitude())
                .queryParam("longitude", geo.longitude())
                .queryParam("current",
                        "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,wind_direction_10m")
                .queryParam("timezone", "Asia/Shanghai")
                .build()
                .toUri();

        WeatherResponse resp = http.get().uri(url).retrieve().body(WeatherResponse.class);
        if (resp == null || resp.current() == null) {
            throw new IllegalStateException("天气服务未返回数据");
        }
        return format(geo, resp.current());
    }

    /** 城市名（支持中文）→ 经纬度。 */
    private Geo geocode(String city) {
        // 用 UriComponentsBuilder 对中文参数做编码，避免 RestClient 把已编码的 % 再次编码。
        URI url = UriComponentsBuilder.fromUriString("https://geocoding-api.open-meteo.com/v1/search")
                .queryParam("count", 1)
                .queryParam("language", "zh")
                .queryParam("format", "json")
                .queryParam("name", city)
                .build()
                .encode(java.nio.charset.StandardCharsets.UTF_8)
                .toUri();

        GeoResponse resp = http.get().uri(url).retrieve().body(GeoResponse.class);
        if (resp == null || resp.results() == null || resp.results().isEmpty()) {
            throw new IllegalArgumentException("未找到城市：" + city);
        }
        var r = resp.results().get(0);
        return new Geo(r.name(), r.latitude(), r.longitude(),
                r.country() == null ? "" : r.country(),
                r.admin1() == null ? "" : r.admin1());
    }

    private String format(Geo geo, Current c) {
        String where = geo.name()
                + (!geo.admin1().isBlank() && !geo.admin1().equals(geo.name()) ? "（" + geo.country() + "·" + geo.admin1() + "）" : "");
        return "%s 当前：%s，气温 %.1f℃（体感 %.1f℃），相对湿度 %d%%，%s风 %.1f km/h。观测时间 %s（数据：Open-Meteo）"
                .formatted(where, describeWeather(c.weatherCode()), c.temperature(), c.apparentTemperature(),
                        c.humidity(), windDirection(c.windDirection()), c.windSpeed(), c.time());
    }

    /** WMO 天气代码 → 中文。包级可见以便单元测试直接覆盖全部分支。 */
    static String describeWeather(int code) {
        return switch (code) {
            case 0 -> "晴";
            case 1 -> "大部晴朗";
            case 2 -> "多云";
            case 3 -> "阴";
            case 45, 48 -> "有雾";
            case 51, 53, 55 -> "毛毛雨";
            case 56, 57 -> "冻毛毛雨";
            case 61 -> "小雨";
            case 63 -> "中雨";
            case 65 -> "大雨";
            case 66, 67 -> "冻雨";
            case 71 -> "小雪";
            case 73 -> "中雪";
            case 75 -> "大雪";
            case 77 -> "米雪";
            case 80 -> "小阵雨";
            case 81 -> "阵雨";
            case 82 -> "强阵雨";
            case 85, 86 -> "阵雪";
            case 95 -> "雷暴";
            case 96, 99 -> "雷暴伴冰雹";
            default -> "未知天气（代码 " + code + "）";
        };
    }

    /** 风向角度 → 八方位中文。包级可见以便单元测试直接覆盖。 */
    static String windDirection(double deg) {
        String[] dirs = {"北", "东北", "东", "东南", "南", "西南", "西", "西北"};
        int idx = (int) Math.round(((deg % 360) / 45.0)) % 8;
        return dirs[idx];
    }

    // ---- JSON 映射记录 ----
    record GeoResponse(List<GeoResult> results) {
    }

    record GeoResult(String name, double latitude, double longitude, String country, String admin1) {
    }

    record WeatherResponse(Current current) {
    }

    record Current(
            String time,
            @JsonProperty("temperature_2m") double temperature,
            @JsonProperty("relative_humidity_2m") int humidity,
            @JsonProperty("apparent_temperature") double apparentTemperature,
            @JsonProperty("weather_code") int weatherCode,
            @JsonProperty("wind_speed_10m") double windSpeed,
            @JsonProperty("wind_direction_10m") double windDirection) {
    }

    private record Geo(String name, double latitude, double longitude, String country, String admin1) {
    }
}
