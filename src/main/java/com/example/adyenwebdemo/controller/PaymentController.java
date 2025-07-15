package com.example.adyenwebdemo.controller;

import com.adyen.service.exception.ApiException;
import com.example.adyenwebdemo.model.RedirectDetailsRequest;
import com.example.adyenwebdemo.model.RedirectDetailsResponse;
import com.example.adyenwebdemo.model.PaymentRequest;
import com.example.adyenwebdemo.model.PaymentSessionResponse;
import com.example.adyenwebdemo.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @Value("${adyen.client.key}")
    private String clientKey;

    /**
     * Renders the homepage with payment form
     */
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("clientKey", clientKey);
        return "index";
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
                RedirectDetailsResponse response = paymentService.submitPaymentDetails(detailsRequest);
                log.info("Payment details processed: {}", response);

                // Add payment result to model
                model.addAttribute("paymentResult", response);

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

        // If no redirectResult, just show success page
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
     * Creates a payment session using Adyen API
     */
    @PostMapping("/api/sessions")
    @ResponseBody
    public ResponseEntity<PaymentSessionResponse> createPaymentSession(
            @RequestBody PaymentRequest paymentRequest, 
            HttpServletRequest request) {
        try {
            log.info("Creating payment session: {}", paymentRequest);

            // Add a reference if none provided
            if (paymentRequest.getReturnUrl() == null) {
                paymentRequest.setReturnUrl(request.getScheme() + "://" + 
                        request.getServerName() + ":" + request.getServerPort() + "/success");
            }

            PaymentSessionResponse response = paymentService.createPaymentSession(paymentRequest);
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
}
