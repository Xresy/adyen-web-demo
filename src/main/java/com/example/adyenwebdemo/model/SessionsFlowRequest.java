package com.example.adyenwebdemo.model;

import lombok.Data;

@Data
public class SessionsFlowRequest {
    private Integer amount;
    private String currency;
    private String countryCode;
    private String returnUrl;
    private boolean enableRecurring;
    private String shopperReference;
}
