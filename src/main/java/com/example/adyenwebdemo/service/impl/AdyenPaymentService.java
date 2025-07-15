package com.example.adyenwebdemo.service.impl;

import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.CreateCheckoutSessionRequest;
import com.adyen.model.checkout.CreateCheckoutSessionResponse;
import com.adyen.model.checkout.PaymentCompletionDetails;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.model.RequestOptions;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.exception.ApiException;
import com.example.adyenwebdemo.config.AdyenConfig;
import com.example.adyenwebdemo.model.RedirectDetailsRequest;
import com.example.adyenwebdemo.model.RedirectDetailsResponse;
import com.example.adyenwebdemo.model.PaymentRequest;
import com.example.adyenwebdemo.model.PaymentSessionResponse;
import com.example.adyenwebdemo.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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
            sessionRequest.shopperReference(paymentRequest.getShopperReference());
            sessionRequest.recurringProcessingModel(CreateCheckoutSessionRequest.RecurringProcessingModelEnum.CARDONFILE);
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

    @Override
    public RedirectDetailsResponse submitPaymentDetails(RedirectDetailsRequest detailsRequest) throws IOException, ApiException {
        log.info("Submitting payment details for redirect result");

        // Create the details object using PaymentCompletionDetails
        PaymentCompletionDetails paymentCompletionDetails = new PaymentCompletionDetails()
            .redirectResult(detailsRequest.getRedirectResult());

        // Create an Adyen PaymentDetailsRequest with the details
        PaymentDetailsRequest adyenDetailsRequest = new PaymentDetailsRequest()
            .details(paymentCompletionDetails);

        // Add payment data if available
        if (detailsRequest.getPaymentData() != null && !detailsRequest.getPaymentData().isEmpty()) {
            adyenDetailsRequest.paymentData(detailsRequest.getPaymentData());
        }

        // Use idempotency key in the request options
        RequestOptions requestOptions = new RequestOptions();
        requestOptions.idempotencyKey(UUID.randomUUID().toString());

        // Call Adyen API to get payment details
        PaymentDetailsResponse response = paymentsApi.paymentsDetails(adyenDetailsRequest, requestOptions);
        log.info("Payment details submitted successfully: {}, result: {}", 
                response.getPspReference(),
                response.getResultCode() != null ? response.getResultCode().toString() : "null");

        // Map Adyen's response to our model - convert ResultCodeEnum to String
        return RedirectDetailsResponse.builder()
                .resultCode(response.getResultCode() != null ? response.getResultCode().toString() : null)
                .pspReference(response.getPspReference())
                .merchantReference(response.getMerchantReference())
                .additionalData(response.getAdditionalData())
                .build();
    }
}
