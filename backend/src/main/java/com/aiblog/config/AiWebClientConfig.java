package com.aiblog.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * AI 网关专用 WebClient：支持流式读取、不整体缓冲响应体（契约 5.4.5）。
 */
@Configuration
public class AiWebClientConfig {

    @Bean
    public WebClient aiWebClient(AiProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("X-Internal-Token", properties.getInternalToken())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
