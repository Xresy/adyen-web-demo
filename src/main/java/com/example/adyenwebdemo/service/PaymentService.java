package com.example.adyenwebdemo.service;

import com.adyen.service.exception.ApiException;
import com.example.adyenwebdemo.model.RedirectDetailsRequest;
import com.example.adyenwebdemo.model.RedirectDetailsResponse;
import com.example.adyenwebdemo.model.PaymentRequest;
import com.example.adyenwebdemo.model.PaymentSessionResponse;

import java.io.IOException;

public interface PaymentService {
    PaymentSessionResponse createPaymentSession(PaymentRequest paymentRequest) throws IOException, ApiException;

    RedirectDetailsResponse submitPaymentDetails(RedirectDetailsRequest detailsRequest) throws IOException, ApiException;
}
