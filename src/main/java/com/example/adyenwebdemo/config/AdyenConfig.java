package com.example.adyenwebdemo.config;

import com.adyen.Client;
import com.adyen.enums.Environment;
import com.adyen.service.checkout.PaymentsApi;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class AdyenConfig {

    @Value("${adyen.api.key}")
    private String apiKey;

    @Value("${adyen.merchant.account}")
    private String merchantAccount;

    @Value("${adyen.client.key}")
    private String clientKey;

    @Value("${adyen.environment}")
    private String environment;

    @Value("${adyen.hmac.key}")
    private String hmacKey;

    @Bean
    public Client adyenClient() {
        // In v39.0.0, the Client constructor and configuration is slightly different
        Client client = new Client(apiKey, Environment.valueOf(environment.toUpperCase()));
        client.setApplicationName("Adyen Web Demo");
        return client;
    }

    @Bean
    public PaymentsApi paymentsApi(Client client) {
        return new PaymentsApi(client);
    }
}
