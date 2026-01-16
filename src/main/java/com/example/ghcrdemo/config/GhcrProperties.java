package com.example.ghcrdemo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * GHCR(GitHub Container Registry) 관련 설정을 바인딩하는 프로퍼티 클래스.
 * 프로퍼티 접두사: `github.container-registry`
 * 간단한 설정값만 포함 (url, timeout).
 */
@Data
@Component
@ConfigurationProperties(prefix = "github.container-registry")
public class GhcrProperties {
    /**
     * GHCR 또는 관련 API의 기본 URL.
     * 예: https://ghcr.io
     */
    private String url;

    /**
     * HTTP 요청 타임아웃(밀리초).
     * 기본값: 30000 (30초)
     */
    private int timeout = 30000;
}
