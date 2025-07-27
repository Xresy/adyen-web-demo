package com.example.adyenwebdemo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdyenPaymentDetailsResponse {
    private String resultCode;
    private String pspReference;
    private String merchantReference;
    private Map<String, String> additionalData;
}
