package com.ecommerce.payment.config;

import com.ecommerce.common.web.CorrelationIdClientInterceptor;
import com.ecommerce.payment.client.OrderServiceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OrderClientConfig {

    @Bean
    public RestClient orderServiceRestClient(RestClient.Builder builder, OrderServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder
                .baseUrl(properties.url())
                .requestFactory(requestFactory)
                .requestInterceptor(new CorrelationIdClientInterceptor())
                .build();
    }
}
