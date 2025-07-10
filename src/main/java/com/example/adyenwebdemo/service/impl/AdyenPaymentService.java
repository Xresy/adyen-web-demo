package com.example.adyenwebdemo.service.impl;

import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.CreateCheckoutSessionRequest;
import com.adyen.model.checkout.CreateCheckoutSessionResponse;
import com.adyen.model.RequestOptions;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.exception.ApiException;
import com.example.adyenwebdemo.config.AdyenConfig;
import com.example.adyenwebdemo.model.PaymentRequest;
import com.example.adyenwebdemo.model.PaymentSessionResponse;
import com.example.adyenwebdemo.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdyenPaymentService implements PaymentService {

    private final PaymentsApi paymentsApi;
    private final AdyenConfig adyenConfig;

    @Value("${adyen.client.key}")
    private String clientKey;

    @Override
    public PaymentSessionResponse createPaymentSession(PaymentRequest paymentRequest) throws IOException, ApiException {
        // Create amount object
        Amount amount = new Amount()
                .currency(paymentRequest.getCurrency())
                .value(paymentRequest.getAmount() * 100L); // Convert to minor units

        // Create checkout session request
        CreateCheckoutSessionRequest sessionRequest = new CreateCheckoutSessionRequest()
                .merchantAccount(adyenConfig.getMerchantAccount())
                .amount(amount)
                .returnUrl(paymentRequest.getReturnUrl())
                .reference("ORDER-" + UUID.randomUUID().toString())
                .countryCode(paymentRequest.getCountryCode());

        // Handle recurring payments if enabled
        if (paymentRequest.isEnableRecurring() && paymentRequest.getShopperReference() != null) {
            sessionRequest.setShopperReference(paymentRequest.getShopperReference());
            sessionRequest.setRecurringProcessingModel(CreateCheckoutSessionRequest.RecurringProcessingModelEnum.CARDONFILE);
        }


        // Call Adyen API to create session
        CreateCheckoutSessionResponse response = paymentsApi.sessions(sessionRequest);
        log.info("Created Adyen payment session: {}", response.getId());

        // Return response object
        return PaymentSessionResponse.builder()
                .sessionId(response.getId())
                .sessionData(response.getSessionData())
                .clientKey(clientKey)
                .build();
    }
}
