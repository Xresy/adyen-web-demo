package com.example.adyenwebdemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response object for payment session creation
 * This class is provided for backward compatibility with existing code
 * and maps to PaymentSessionResponse.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentResponse {
    private String sessionData;
    private String sessionId;
    private String clientKey;
}
