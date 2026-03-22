package com.incoin.demo.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // Connection pool: 100 total, 20 per route (handles 20+ concurrent users fine)
        PoolingHttpClientConnectionManager connManager =
            new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(100);
        connManager.setDefaultMaxPerRoute(20);

        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connManager)
            .build();

        HttpComponentsClientHttpRequestFactory factory =
            new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(5_000);          // 5 s connect
        factory.setConnectionRequestTimeout(5_000); // 5 s get-from-pool

        return new RestTemplate(factory);
    }
}
