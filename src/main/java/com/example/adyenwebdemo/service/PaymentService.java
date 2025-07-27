package com.example.adyenwebdemo.service;

import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.AuthenticationData;
import com.adyen.model.checkout.CreateCheckoutSessionRequest;
import com.adyen.model.checkout.CreateCheckoutSessionResponse;
import com.adyen.model.checkout.PaymentCompletionDetails;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.model.checkout.ThreeDSRequestData;
import com.adyen.model.RequestOptions;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.exception.ApiException;
import com.example.adyenwebdemo.config.AdyenConfig;
import com.example.adyenwebdemo.model.RedirectDetailsRequest;
import com.example.adyenwebdemo.model.AdyenPaymentDetailsResponse;
import com.example.adyenwebdemo.model.PaymentRequest;
import com.example.adyenwebdemo.model.PaymentSessionResponse;
import com.example.adyenwebdemo.model.ThreeDSDetailsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentsApi paymentsApi;
    private final AdyenConfig adyenConfig;

    @Value("${adyen.client.key}")
    private String clientKey;

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
                .shopperReference(paymentRequest.getShopperReference())
                .countryCode(paymentRequest.getCountryCode());

        // Enable 3DS authentication
        ThreeDSRequestData threeDSRequestData = new ThreeDSRequestData();
        threeDSRequestData.nativeThreeDS(ThreeDSRequestData.NativeThreeDSEnum.DISABLED);
        AuthenticationData authData = new AuthenticationData();
        authData.threeDSRequestData(threeDSRequestData);
        sessionRequest.authenticationData(authData);

        // Handle recurring payments if enabled
        if (paymentRequest.isEnableRecurring() && paymentRequest.getShopperReference() != null) {
            sessionRequest.recurringProcessingModel(CreateCheckoutSessionRequest.RecurringProcessingModelEnum.CARDONFILE);
            sessionRequest.setShopperInteraction(CreateCheckoutSessionRequest.ShopperInteractionEnum.ECOMMERCE);
            sessionRequest.setStorePaymentMethodMode(CreateCheckoutSessionRequest.StorePaymentMethodModeEnum.ENABLED);
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

    public AdyenPaymentDetailsResponse submitPaymentDetails(RedirectDetailsRequest detailsRequest) throws IOException, ApiException {
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
        log.info("Submitting 3DS authentication details");

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

        // Call Adyen API to process 3DS result
        PaymentDetailsResponse response = paymentsApi.paymentsDetails(adyenDetailsRequest, requestOptions);
        log.info("3DS details submitted successfully: {}, result: {}", 
                response.getPspReference(),
                response.getResultCode() != null ? response.getResultCode().toString() : "null");

        // Map Adyen's response to our model
        return AdyenPaymentDetailsResponse.builder()
                .resultCode(response.getResultCode() != null ? response.getResultCode().toString() : null)
                .pspReference(response.getPspReference())
                .merchantReference(response.getMerchantReference())
                .additionalData(response.getAdditionalData())
                .build();
    }
}
