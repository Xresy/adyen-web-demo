package com.example.adyenwebdemo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedirectDetailsRequest {
    private String redirectResult;
    private String paymentData;
    // Note: We don't need to include details field here as it's handled
    // differently in the actual Adyen API call using PaymentCompletionDetails
}
