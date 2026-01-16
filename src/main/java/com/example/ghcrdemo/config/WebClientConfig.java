package com.example.ghcrdemo.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient 구성 클래스.
 * GHCR 또는 관련 API 호출에 사용될 기본 WebClient 빈을 생성한다.
 * 타임아웃과 메모리 제한, 기본 헤더(JSON) 등을 설정함.
 */
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    /**
     * GHCR 관련 설정 프로퍼티(예: url, timeout).
     */
    private final GhcrProperties ghcrProperties;

    /**
     * 애플리케이션에서 사용할 WebClient 빈을 생성한다.
     * <p>
     * - 연결 타임아웃: `ghcrProperties.getTimeout()` 밀리초
     * - 응답 타임아웃: 동일한 값
     * - 읽기/쓰기 타임아웃 핸들러 추가
     * - 최대 메모리 버퍼: 16MB (응답 바디용)
     * - 기본 헤더: Accept/Content-Type = application/json
     *
     * @return 구성된 WebClient 인스턴스
     */
    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, ghcrProperties.getTimeout())
                .responseTimeout(Duration.ofMillis(ghcrProperties.getTimeout()))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(ghcrProperties.getTimeout(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(ghcrProperties.getTimeout(), TimeUnit.MILLISECONDS)));

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return WebClient.builder()
                .baseUrl(ghcrProperties.getUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
