package com.example.adyenwebdemo.service;

import com.adyen.model.checkout.*;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.exception.ApiException;
import com.example.adyenwebdemo.config.AdyenConfig;
import com.example.adyenwebdemo.model.SessionsFlowRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedFlowService {

    private final PaymentsApi paymentsApi;
    private final AdyenConfig adyenConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get available payment methods for the advanced flow
     */
    public Map<String, Object> getPaymentMethods(SessionsFlowRequest paymentRequest) throws IOException, ApiException {
        // Create amount object
        Amount amount = new Amount()
                .currency(paymentRequest.getCurrency())
                .value(paymentRequest.getAmount() * 100L); // Convert to minor units

        // Create payment methods request
        PaymentMethodsRequest paymentMethodsRequest = new PaymentMethodsRequest()
                .merchantAccount(adyenConfig.getMerchantAccount())
                .amount(amount)
                .countryCode(paymentRequest.getCountryCode())
                .shopperLocale("en-US");

        // Add shopper reference for recurring payments
        if (paymentRequest.getShopperReference() != null && !paymentRequest.getShopperReference().isEmpty()) {
            paymentMethodsRequest.shopperReference(paymentRequest.getShopperReference());
        }

        // Log request details
        log.info("=== ADYEN PAYMENT METHODS API REQUEST ===");
        log.info("Merchant Account: {}", paymentMethodsRequest.getMerchantAccount());
        log.info("Amount: {} {}", amount.getValue(), amount.getCurrency());
        log.info("Country Code: {}", paymentMethodsRequest.getCountryCode());
        log.info("Shopper Reference: {}", paymentMethodsRequest.getShopperReference());
        try {
            log.info("Full Request JSON: {}", objectMapper.writeValueAsString(paymentMethodsRequest));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize payment methods request to JSON", e);
        }

        // Call Adyen API to get payment methods
        PaymentMethodsResponse response = paymentsApi.paymentMethods(paymentMethodsRequest);
        
        // Log detailed response
        log.info("=== ADYEN PAYMENT METHODS API RESPONSE ===");
        log.info("Payment Methods Count: {}", response.getPaymentMethods() != null ? response.getPaymentMethods().size() : 0);
        log.info("Stored Payment Methods Count: {}", response.getStoredPaymentMethods() != null ? response.getStoredPaymentMethods().size() : 0);
        try {
            log.info("Full Response JSON: {}", objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize payment methods response to JSON", e);
        }

        // Convert response to map for JSON serialization
        Map<String, Object> result = new HashMap<>();
        result.put("paymentMethods", response.getPaymentMethods());
        result.put("storedPaymentMethods", response.getStoredPaymentMethods());
        
        return result;
    }

    /**
     * Make a payment using the advanced flow
     */
    public Map<String, Object> makePayment(Map<String, Object> paymentData) throws IOException, ApiException {
        log.info("=== ADYEN PAYMENTS API REQUEST ===");
        
        // Extract payment details from the request
        Map<String, Object> amountData = (Map<String, Object>) paymentData.get("amount");
        Amount amount = new Amount()
                .currency((String) amountData.get("currency"))
                .value(((Number) amountData.get("value")).longValue());
        
        log.info("Amount: {} {}", amount.getValue(), amount.getCurrency());
        log.info("Return URL: {}", paymentData.get("returnUrl"));
        log.info("Shopper Reference: {}", paymentData.get("shopperReference"));
        log.info("Country Code: {}", paymentData.get("countryCode"));
        log.info("Enable Recurring: {}", paymentData.get("enableRecurring"));

        // Create payment request using Adyen's PaymentRequest class
        PaymentRequest adyenPaymentRequest = new PaymentRequest()
                .merchantAccount(adyenConfig.getMerchantAccount())
                .amount(amount)
                .reference("ORDER-" + UUID.randomUUID().toString())
                .returnUrl((String) paymentData.get("returnUrl"));

        // Add payment method details
        boolean isUsingStoredPaymentMethod = false;
        if (paymentData.containsKey("paymentMethod")) {
            try {
                // Convert payment method data to JSON string and deserialize using Adyen's method
                String paymentMethodJson = convertMapToJson(paymentData.get("paymentMethod"));
                CheckoutPaymentMethod paymentMethod = CheckoutPaymentMethod.fromJson(paymentMethodJson);
                
                // Check if this is a stored payment method
                Map<String, Object> paymentMethodData = (Map<String, Object>) paymentData.get("paymentMethod");
                if (paymentMethodData.containsKey("storedPaymentMethodId") || 
                    paymentMethodData.containsKey("recurringDetailReference")) {
                    isUsingStoredPaymentMethod = true;
                    log.info("Using stored payment method: {}", 
                            paymentMethodData.get("storedPaymentMethodId") != null ? 
                            paymentMethodData.get("storedPaymentMethodId") : 
                            paymentMethodData.get("recurringDetailReference"));
                }
                
                adyenPaymentRequest.paymentMethod(paymentMethod);
            } catch (Exception e) {
                log.error("Error processing payment method data", e);
                throw new RuntimeException("Invalid payment method data", e);
            }
        }


        // Add shopper reference if provided
        if (paymentData.containsKey("shopperReference")) {
            adyenPaymentRequest.shopperReference((String) paymentData.get("shopperReference"));
        }

        // Add country code if provided
        if (paymentData.containsKey("countryCode")) {
            adyenPaymentRequest.countryCode((String) paymentData.get("countryCode"));
        }

        // Configure recurring and stored payment method settings
        boolean enableRecurring = paymentData.containsKey("enableRecurring") && (Boolean) paymentData.get("enableRecurring");
        
        if (isUsingStoredPaymentMethod) {
            // Using existing stored payment method
            log.info("=== STORED PAYMENT METHOD DETECTED ===");
            adyenPaymentRequest.recurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.CARDONFILE);
            adyenPaymentRequest.shopperInteraction(PaymentRequest.ShopperInteractionEnum.CONTAUTH);
            adyenPaymentRequest.storePaymentMethod(false); // Don't store again
        } else if (enableRecurring) {
            // New payment with recurring enabled - store for future use
            adyenPaymentRequest.recurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.CARDONFILE);
            adyenPaymentRequest.shopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE);
            adyenPaymentRequest.storePaymentMethod(true); // Store this payment method
        }

        // Add browser info for 3DS
        if (paymentData.containsKey("browserInfo")) {
            Map<String, Object> browserInfoData = (Map<String, Object>) paymentData.get("browserInfo");
            BrowserInfo browserInfo = new BrowserInfo()
                    .userAgent((String) browserInfoData.get("userAgent"))
                    .acceptHeader((String) browserInfoData.get("acceptHeader"))
                    .language((String) browserInfoData.get("language"))
                    .colorDepth(((Number) browserInfoData.get("colorDepth")).intValue())
                    .screenHeight(((Number) browserInfoData.get("screenHeight")).intValue())
                    .screenWidth(((Number) browserInfoData.get("screenWidth")).intValue())
                    .timeZoneOffset(((Number) browserInfoData.get("timeZoneOffset")).intValue())
                    .javaEnabled((Boolean) browserInfoData.get("javaEnabled"));
            
            adyenPaymentRequest.browserInfo(browserInfo);

        }

        // Determine 3DS native or redirect
        ThreeDSRequestData threeDSRequestData = new ThreeDSRequestData();
        threeDSRequestData.nativeThreeDS(ThreeDSRequestData.NativeThreeDSEnum.PREFERRED);
        AuthenticationData authData = new AuthenticationData();
        authData.threeDSRequestData(threeDSRequestData);
        adyenPaymentRequest.authenticationData(authData);
        adyenPaymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);
        adyenPaymentRequest.setOrigin("http://localhost:8080");

        // Log full request
        try {
            log.info("Full Request JSON: {}", objectMapper.writeValueAsString(adyenPaymentRequest));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize payment request to JSON", e);
        }

        // Call Adyen API to make payment
        PaymentResponse response = paymentsApi.payments(adyenPaymentRequest);
        
        // Log detailed response
        log.info("=== ADYEN PAYMENTS API RESPONSE ===");
        log.info("PSP Reference: {}", response.getPspReference());
        log.info("Result Code: {}", response.getResultCode() != null ? response.getResultCode().toString() : "null");
        log.info("Merchant Reference: {}", response.getMerchantReference());
        log.info("Has Action: {}", response.getAction() != null);
        if (response.getAction() != null) {
            // PaymentResponseAction doesn't have getType(), log the action object instead
            log.info("Action Details: {}", response.getAction());
        }
        if (response.getAdditionalData() != null) {
            log.info("Additional Data: {}", response.getAdditionalData());
        }
        try {
            log.info("Full Response JSON: {}", objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize payment response to JSON", e);
        }

        // Convert response to map for frontend following Adyen's official example format
        Map<String, Object> result = new HashMap<>();
        result.put("resultCode", response.getResultCode() != null ? response.getResultCode().toString() : null);
        result.put("pspReference", response.getPspReference());
        result.put("merchantReference", response.getMerchantReference());
        
        // Add action if present (for 3DS, redirects, etc.)
        if (response.getAction() != null) {
            result.put("action", response.getAction());
        }
        
        // Add order if present (for some payment methods)
        if (response.getOrder() != null) {
            result.put("order", response.getOrder());
        }
        
        // Add donationToken if present (for donation flows)
        if (response.getDonationToken() != null) {
            result.put("donationToken", response.getDonationToken());
        }
        
        // Add additional data
        if (response.getAdditionalData() != null) {
            result.put("additionalData", response.getAdditionalData());
        }

        return result;
    }

    /**
     * Submit payment details for advanced flow (3DS, redirects, etc.)
     */
    public Map<String, Object> submitPaymentDetails(Map<String, Object> detailsData) throws IOException, ApiException {
        log.info("=== ADYEN PAYMENT DETAILS API REQUEST (Advanced Flow) ===");
        log.info("Payment Data: {}", detailsData.get("paymentData"));
        log.info("Redirect Result: {}", detailsData.get("redirectResult"));
        log.info("3DS Result: {}", detailsData.get("threeDSResult"));

        // Create payment details request
        PaymentDetailsRequest paymentDetailsRequest = new PaymentDetailsRequest();
        
        // Add payment data
        if (detailsData.containsKey("paymentData")) {
            paymentDetailsRequest.paymentData((String) detailsData.get("paymentData"));
        }

        // Create details object
        PaymentCompletionDetails details = new PaymentCompletionDetails();
        
        // Handle different types of details
        if (detailsData.containsKey("redirectResult")) {
            details.redirectResult((String) detailsData.get("redirectResult"));
        }
        
        if (detailsData.containsKey("threeDSResult")) {
            details.threeDSResult((String) detailsData.get("threeDSResult"));
        }
        
        // Add any additional details
//        detailsData.forEach((key, value) -> {
//            if (!key.equals("paymentData") && !key.equals("redirectResult") && !key.equals("threeDSResult")) {
//                details.put(key, value);
//            }
//        });
        
        paymentDetailsRequest.details(details);

        // Log full request
        try {
            log.info("Full Request JSON: {}", objectMapper.writeValueAsString(paymentDetailsRequest));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize payment details request to JSON", e);
        }

        // Call Adyen API
        PaymentDetailsResponse response = paymentsApi.paymentsDetails(paymentDetailsRequest);
        
        // Log detailed response
        log.info("=== ADYEN PAYMENT DETAILS API RESPONSE (Advanced Flow) ===");
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

        // Convert response to map
        Map<String, Object> result = new HashMap<>();
        result.put("resultCode", response.getResultCode() != null ? response.getResultCode().toString() : null);
        result.put("pspReference", response.getPspReference());
        result.put("merchantReference", response.getMerchantReference());
        
        if (response.getAdditionalData() != null) {
            result.put("additionalData", response.getAdditionalData());
        }

        return result;
    }

    /**
     * Helper method to convert Map to JSON string
     */
    private String convertMapToJson(Object data) throws IOException {
        return objectMapper.writeValueAsString(data);
    }
}
