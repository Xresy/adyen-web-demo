package com.example.adyenwebdemo.service;

import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.AuthenticationData;
import com.adyen.model.checkout.CreateCheckoutSessionRequest;
import com.adyen.model.checkout.CreateCheckoutSessionResponse;
import com.adyen.model.checkout.PaymentCompletionDetails;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.model.checkout.SessionResultResponse;
import com.adyen.model.checkout.ThreeDSRequestData;
import com.adyen.model.RequestOptions;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.exception.ApiException;
import com.example.adyenwebdemo.config.AdyenConfig;
import com.example.adyenwebdemo.model.RedirectDetailsRequest;
import com.example.adyenwebdemo.model.AdyenPaymentDetailsResponse;
import com.example.adyenwebdemo.model.SessionsFlowRequest;
import com.example.adyenwebdemo.model.SessionsFlowResponse;
import com.example.adyenwebdemo.model.ThreeDSDetailsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionsFlowService {

    private final PaymentsApi paymentsApi;
    private final AdyenConfig adyenConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${adyen.client.key}")
    private String clientKey;

    public SessionsFlowResponse createPaymentSession(SessionsFlowRequest paymentRequest) throws IOException, ApiException {
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
                .shopperReference(paymentRequest.getShopperReference())
                .countryCode(paymentRequest.getCountryCode());

        // Enable 3DS authentication
        ThreeDSRequestData threeDSRequestData = new ThreeDSRequestData();
        threeDSRequestData.nativeThreeDS(ThreeDSRequestData.NativeThreeDSEnum.PREFERRED);
        AuthenticationData authData = new AuthenticationData();
        authData.threeDSRequestData(threeDSRequestData);
        sessionRequest.authenticationData(authData);

        // Handle recurring payments if enabled
        if (paymentRequest.isEnableRecurring() && paymentRequest.getShopperReference() != null) {
            sessionRequest.recurringProcessingModel(CreateCheckoutSessionRequest.RecurringProcessingModelEnum.CARDONFILE);
            sessionRequest.setShopperInteraction(CreateCheckoutSessionRequest.ShopperInteractionEnum.ECOMMERCE);
            sessionRequest.setStorePaymentMethodMode(CreateCheckoutSessionRequest.StorePaymentMethodModeEnum.ENABLED);
        }

        // Log request details
        log.info("=== ADYEN SESSIONS API REQUEST ===");
        log.info("Merchant Account: {}", sessionRequest.getMerchantAccount());
        log.info("Amount: {} {}", amount.getValue(), amount.getCurrency());
        log.info("Return URL: {}", sessionRequest.getReturnUrl());
        log.info("Reference: {}", sessionRequest.getReference());
        log.info("Country Code: {}", sessionRequest.getCountryCode());
        log.info("Shopper Reference: {}", sessionRequest.getShopperReference());
        log.info("Recurring Enabled: {}", paymentRequest.isEnableRecurring());
        try {
            log.info("Full Request JSON: {}", objectMapper.writeValueAsString(sessionRequest));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize session request to JSON", e);
        }

        // Call Adyen API to create session
        CreateCheckoutSessionResponse response = paymentsApi.sessions(sessionRequest);
        
        // Log response details
        log.info("=== ADYEN SESSIONS API RESPONSE ===");
        log.info("Session ID: {}", response.getId());
        log.info("Session Data Length: {}", response.getSessionData() != null ? response.getSessionData().length() : 0);
        try {
            log.info("Full Response JSON: {}", objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize session response to JSON", e);
        }

        // Return response object
        return SessionsFlowResponse.builder()
                .sessionId(response.getId())
                .sessionData(response.getSessionData())
                .clientKey(clientKey)
                .build();
    }

    /**
     * Get the result of a payment session using session ID and session result
     */
    public AdyenPaymentDetailsResponse getSessionResult(String sessionId, String sessionResult) throws IOException, ApiException {
        log.info("=== ADYEN GET SESSION RESULT API REQUEST ===");
        log.info("Session ID: {}", sessionId);
        log.info("Session Result: {}", sessionResult);

        // Call Adyen API to get session result
        SessionResultResponse response = paymentsApi.getResultOfPaymentSession(sessionId, sessionResult, null);
        
        // Log detailed response
        log.info("=== ADYEN GET SESSION RESULT API RESPONSE ===");
        log.info("Session ID: {}", response.getId());
        log.info("Session Status: {}", response.getStatus() != null ? response.getStatus().toString() : "null");
        log.info("Session Reference: {}", response.getReference());
        log.info("Payments Count: {}", response.getPayments() != null ? response.getPayments().size() : 0);
        if (response.getAdditionalData() != null) {
            log.info("Additional Data: {}", response.getAdditionalData());
        }
        try {
            log.info("Full Response JSON: {}", objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize session result response to JSON", e);
        }

        // Extract payment details from the first payment in the list
        String resultCode = null;
        String pspReference = null;
        String merchantReference = response.getReference();
        Map<String, String> additionalData = response.getAdditionalData();

        if (response.getPayments() != null && !response.getPayments().isEmpty()) {
            // Get the first (and typically only) payment
            var payment = response.getPayments().get(0);
            // Payment object doesn't have getStatus(), use resultCode from payment
            if (payment.getResultCode() != null) {
                resultCode = payment.getResultCode().toString();
            }
            pspReference = payment.getPspReference();
            // Payment object doesn't have getAdditionalData(), use session-level additionalData
            // additionalData is already set from response.getAdditionalData() above
        }

        // Map session status to result code if no payment result code available
        if (resultCode == null && response.getStatus() != null) {
            switch (response.getStatus()) {
                case COMPLETED:
                    resultCode = "Authorised";
                    break;
                case PAYMENTPENDING:
                    resultCode = "Pending";
                    break;
                case REFUSED:
                    resultCode = "Refused";
                    break;
                case CANCELED:
                    resultCode = "Cancelled";
                    break;
                case EXPIRED:
                    resultCode = "Expired";
                    break;
                default:
                    resultCode = response.getStatus().toString();
            }
        }

        log.info("Extracted payment details - Result: {}, PSP Reference: {}", resultCode, pspReference);

        // Map to our model
        return AdyenPaymentDetailsResponse.builder()
                .resultCode(resultCode)
                .pspReference(pspReference)
                .merchantReference(merchantReference)
                .additionalData(additionalData)
                .build();
    }

    public AdyenPaymentDetailsResponse submitPaymentDetails(RedirectDetailsRequest detailsRequest) throws IOException, ApiException {
        log.info("=== ADYEN PAYMENT DETAILS API REQUEST ===");
        log.info("Redirect Result: {}", detailsRequest.getRedirectResult());
        log.info("Payment Data: {}", detailsRequest.getPaymentData());

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

        // Log request details
        try {
            log.info("Full Request JSON: {}", objectMapper.writeValueAsString(adyenDetailsRequest));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize payment details request to JSON", e);
        }

        // Call Adyen API to get payment details
        PaymentDetailsResponse response = paymentsApi.paymentsDetails(adyenDetailsRequest, requestOptions);
        
        // Log detailed response
        log.info("=== ADYEN PAYMENT DETAILS API RESPONSE ===");
        log.info("PSP Reference: {}", response.getPspReference());
        log.info("Result Code: {}", response.getResultCode() != null ? response.getResultCode().toString() : "null");
        log.info("Merchant Reference: {}", response.getMerchantReference());
        if (response.getAdditionalData() != null) {
            log.info("Additional Data: {}", response.getAdditionalData());
        }
        try {
            log.info("Full Response JSON: {}", objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize payment details response to JSON", e);
        }

        // Map Adyen's response to our model - convert ResultCodeEnum to String
        return AdyenPaymentDetailsResponse.builder()
                .resultCode(response.getResultCode() != null ? response.getResultCode().toString() : null)
                .pspReference(response.getPspReference())
                .merchantReference(response.getMerchantReference())
                .additionalData(response.getAdditionalData())
                .build();
    }

    /**
     * Process 3DS authentication results
     * This is separate from redirect results and specifically handles 3DS authentication
     */
    public AdyenPaymentDetailsResponse submit3DSDetails(ThreeDSDetailsRequest detailsRequest) throws IOException, ApiException {
        log.info("=== ADYEN 3DS DETAILS API REQUEST ===");
        log.info("3DS Result: {}", detailsRequest.getThreeDSResult());
        log.info("Payment Data: {}", detailsRequest.getPaymentData());

        // Create the details object using PaymentCompletionDetails for 3DS
        PaymentCompletionDetails paymentCompletionDetails = new PaymentCompletionDetails()
            .threeDSResult(detailsRequest.getThreeDSResult());

        // Create an Adyen PaymentDetailsRequest with the 3DS details
        PaymentDetailsRequest adyenDetailsRequest = new PaymentDetailsRequest()
            .details(paymentCompletionDetails);

        // Add payment data if available
        if (detailsRequest.getPaymentData() != null && !detailsRequest.getPaymentData().isEmpty()) {
            adyenDetailsRequest.paymentData(detailsRequest.getPaymentData());
        }

        // Use idempotency key in the request options
        RequestOptions requestOptions = new RequestOptions();
        requestOptions.idempotencyKey(UUID.randomUUID().toString());

        // Log request details
        try {
            log.info("Full Request JSON: {}", objectMapper.writeValueAsString(adyenDetailsRequest));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize 3DS details request to JSON", e);
        }

        // Call Adyen API to process 3DS result
        PaymentDetailsResponse response = paymentsApi.paymentsDetails(adyenDetailsRequest, requestOptions);
        
        // Log detailed response
        log.info("=== ADYEN 3DS DETAILS API RESPONSE ===");
        log.info("PSP Reference: {}", response.getPspReference());
        log.info("Result Code: {}", response.getResultCode() != null ? response.getResultCode().toString() : "null");
        log.info("Merchant Reference: {}", response.getMerchantReference());
        if (response.getAdditionalData() != null) {
            log.info("Additional Data: {}", response.getAdditionalData());
        }
        try {
            log.info("Full Response JSON: {}", objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize 3DS details response to JSON", e);
        }

        // Map Adyen's response to our model
        return AdyenPaymentDetailsResponse.builder()
                .resultCode(response.getResultCode() != null ? response.getResultCode().toString() : null)
                .pspReference(response.getPspReference())
                .merchantReference(response.getMerchantReference())
                .additionalData(response.getAdditionalData())
                .build();
    }
}
