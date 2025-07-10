package com.example.adyenwebdemo.service;

import com.adyen.model.checkout.CreateCheckoutSessionResponse;
import com.adyen.service.exception.ApiException;
import com.example.adyenwebdemo.model.PaymentRequest;
import com.example.adyenwebdemo.model.PaymentSessionResponse;

import java.io.IOException;

public interface PaymentService {
    PaymentSessionResponse createPaymentSession(PaymentRequest paymentRequest) throws IOException, ApiException;
}
