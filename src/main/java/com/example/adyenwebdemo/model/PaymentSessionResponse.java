package com.example.adyenwebdemo.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentSessionResponse {
    private String sessionId;
    private String sessionData;
    private String clientKey;
}
