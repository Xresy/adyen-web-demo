package com.example.adyenwebdemo.controller;

import com.adyen.service.exception.ApiException;
import com.example.adyenwebdemo.model.RedirectDetailsRequest;
import com.example.adyenwebdemo.model.AdyenPaymentDetailsResponse;
import com.example.adyenwebdemo.model.SessionsFlowRequest;
import com.example.adyenwebdemo.model.SessionsFlowResponse;
import com.example.adyenwebdemo.model.ThreeDSDetailsRequest;
import com.example.adyenwebdemo.service.SessionsFlowService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

import java.util.Collections;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class SessionsFlowController {

    private final SessionsFlowService sessionsFlowService;

    @Value("${adyen.client.key}")
    private String clientKey;

    /**
     * Renders the flow selection page
     */
    @GetMapping("/")
    public String home() {
        return "flow-selection";
    }

    /**
     * Renders the sessions flow payment form
     */
    @GetMapping("/sessions")
    public String sessionsFlow(Model model) {
        model.addAttribute("clientKey", clientKey);
        return "sessions-flow";
    }

    /**
     * Handles redirects from Adyen payment flow
     * This endpoint processes the redirectResult parameter
     */
    @GetMapping("/success")
    public String handleRedirect(
            @RequestParam(value = "redirectResult", required = false) String redirectResult,
            Model model) {

        // If there's a redirectResult, we need to handle it
        if (redirectResult != null && !redirectResult.isEmpty()) {
            try {
                log.info("Received redirect from Adyen with redirectResult parameter");

                // Create a redirect details request
                RedirectDetailsRequest detailsRequest = RedirectDetailsRequest.builder()
                        .redirectResult(redirectResult)
                        .build();

                // Submit details to Adyen
                AdyenPaymentDetailsResponse response = sessionsFlowService.submitPaymentDetails(detailsRequest);
                log.info("Payment details processed: {}", response);

                // Add payment result to model
                model.addAttribute("paymentResult", response);
                log.info("Added payment result to model: {}", response);

                // Determine where to redirect based on result code
                if (response.getResultCode() != null) {
                    String resultCode = response.getResultCode().toUpperCase();
                    if (resultCode.equals("AUTHORISED") || resultCode.equals("AUTHENTICATED")) {
                        return "success";
                    } else if (resultCode.equals("PENDING") || resultCode.equals("RECEIVED")) {
                        return "pending";
                    } else {
                        model.addAttribute("error", "Payment was not successful: " + resultCode);
                        return "failed";
                    }
                }
            } catch (Exception e) {
                log.error("Error processing redirect result", e);
                model.addAttribute("error", "Error processing payment: " + e.getMessage());
                return "failed";
            }
        }

        // If no redirectResult, just show success page with empty model
        // This will allow the client-side JavaScript to populate from sessionStorage
        // without causing Thymeleaf errors
        log.info("No redirectResult parameter, showing success page with client-side data");
        model.addAttribute("paymentResult", null);
        return "success";
    }

    /**
     * Renders the payment failed page
     */
    @GetMapping("/failed")
    public String failed() {
        return "failed";
    }

    /**
     * Renders the payment pending page
     */
    @GetMapping("/pending")
    public String pending() {
        return "pending";
    }

    /**
     * Handle unified result page for both flows
     */
    @GetMapping("/result")
    public String handleUnifiedResult(
            @RequestParam(value = "redirectResult", required = false) String redirectResult,
            Model model) {

        // If there's a redirectResult, we need to handle it
        if (redirectResult != null && !redirectResult.isEmpty()) {
            try {
                log.info("Received redirect from Adyen with redirectResult parameter");

                // Create a redirect details request
                RedirectDetailsRequest detailsRequest = RedirectDetailsRequest.builder()
                        .redirectResult(redirectResult)
                        .build();

                // Submit details to Adyen
                AdyenPaymentDetailsResponse response = sessionsFlowService.submitPaymentDetails(detailsRequest);
                log.info("Payment details processed: {}", response);

                // Add payment result to model
                model.addAttribute("paymentResult", response);
                log.info("Added payment result to model: {}", response);

                // Determine where to redirect based on result code
                if (response.getResultCode() != null) {
                    String resultCode = response.getResultCode().toUpperCase();
                    if (resultCode.equals("AUTHORISED") || resultCode.equals("AUTHENTICATED")) {
                        return "success";
                    } else if (resultCode.equals("PENDING") || resultCode.equals("RECEIVED")) {
                        return "pending";
                    } else {
                        model.addAttribute("error", "Payment was not successful: " + resultCode);
                        return "failed";
                    }
                }
            } catch (Exception e) {
                log.error("Error processing redirect result", e);
                model.addAttribute("error", "Error processing payment: " + e.getMessage());
                return "failed";
            }
        }

        // If no redirectResult, show success page with client-side data handling
        log.info("No redirectResult parameter, showing success page with client-side data");
        model.addAttribute("paymentResult", null);
        return "success";
    }

    /**
     * Creates a payment session using Adyen API
     */
    @PostMapping("/api/sessions")
    @ResponseBody
    public ResponseEntity<SessionsFlowResponse> createPaymentSession(
            @RequestBody SessionsFlowRequest paymentRequest, 
            HttpServletRequest request) {
        // Manual validation
        if (paymentRequest.getShopperReference() == null || paymentRequest.getShopperReference().trim().isEmpty()) {
            log.error("Missing required field: shopperReference");
            return ResponseEntity.badRequest().build();
        }

        try {
            log.info("Creating payment session: {}", paymentRequest);

            // Add a reference if none provided
            if (paymentRequest.getReturnUrl() == null) {
                paymentRequest.setReturnUrl(request.getScheme() + "://" + 
                        request.getServerName() + ":" + request.getServerPort() + "/success");
            }

            SessionsFlowResponse response = sessionsFlowService.createPaymentSession(paymentRequest);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error creating payment session", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Handles Adyen webhook notifications
     */
    @PostMapping("/api/payments/webhook")
    @ResponseBody
    public ResponseEntity<?> webhook(@RequestBody String payload) {
        // In a real application, you should validate the HMAC signature
        // and process the webhook notification
        log.info("Received webhook notification: {}", payload);
        return ResponseEntity.ok(Collections.singletonMap("notificationResponse", "[accepted]"));
    }

    /**
     * Handle payment details submission from frontend
     * This endpoint processes the redirectResult from Adyen redirect payment methods
     */
    @PostMapping("/api/payments/details")
    @ResponseBody
    public ResponseEntity<AdyenPaymentDetailsResponse> paymentDetails(
            @RequestBody RedirectDetailsRequest detailsRequest) {
        try {
            log.info("Submitting payment details: {}", detailsRequest);
            AdyenPaymentDetailsResponse response = sessionsFlowService.submitPaymentDetails(detailsRequest);
            log.info("Details processed with result: {}", response.getResultCode());
            return ResponseEntity.ok(response);
        } catch (IOException | ApiException e) {
            log.error("Error submitting payment details", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Handle 3DS authentication details submission from frontend
     * This endpoint processes the threeDSResult from Adyen 3DS authentication
     */
    @PostMapping("/api/payments/3DSDetails")
    @ResponseBody
    public ResponseEntity<AdyenPaymentDetailsResponse> threeDSDetails(
            @RequestBody ThreeDSDetailsRequest detailsRequest) {
        try {
            log.info("Submitting 3DS details: {}", detailsRequest);
            AdyenPaymentDetailsResponse response = sessionsFlowService.submit3DSDetails(detailsRequest);
            log.info("3DS details processed with result: {}", response.getResultCode());
            return ResponseEntity.ok(response);
        } catch (IOException | ApiException e) {
            log.error("Error submitting 3DS details", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get session result from Adyen using session ID and session result
     */
    @PostMapping("/api/sessions/result")
    @ResponseBody
    public ResponseEntity<AdyenPaymentDetailsResponse> getSessionResult(
            @RequestBody Map<String, String> request) {
        try {
            String sessionId = request.get("sessionId");
            String sessionResult = request.get("sessionResult");
            
            if (sessionId == null || sessionResult == null) {
                log.error("Missing sessionId or sessionResult in request");
                return ResponseEntity.badRequest().build();
            }
            
            log.info("Getting session result for session: {}", sessionId);
            AdyenPaymentDetailsResponse response = sessionsFlowService.getSessionResult(sessionId, sessionResult);
            log.info("Session result retrieved with result: {}", response.getResultCode());
            return ResponseEntity.ok(response);
        } catch (IOException | ApiException e) {
            log.error("Error getting session result", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
