package com.example.springaidemo.dto;

import java.util.List;

/**
 * 结构化输出示例：模型返回的 JSON 会被自动映射为该记录。
 *
 * @param actor 演员姓名
 * @param films 电影列表
 */
public record ActorFilms(String actor, List<String> films) {
}
