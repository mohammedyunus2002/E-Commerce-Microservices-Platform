package com.ecommerce.inventory.config;

import com.ecommerce.common.web.CorrelationIdClientInterceptor;
import com.ecommerce.inventory.client.ProductServiceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ProductClientConfig {

    @Bean
    public RestClient productServiceRestClient(RestClient.Builder builder, ProductServiceProperties properties) {
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
