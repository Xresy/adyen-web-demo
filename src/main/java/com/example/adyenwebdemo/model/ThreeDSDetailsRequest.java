package com.example.adyenwebdemo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreeDSDetailsRequest {
    private String threeDSResult;
    private String paymentData;
}
