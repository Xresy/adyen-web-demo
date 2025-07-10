package com.example.adyenwebdemo.controller;

import com.adyen.model.checkout.CreateCheckoutSessionRequest;
import com.adyen.model.checkout.CreateCheckoutSessionResponse;
import com.adyen.service.exception.ApiException;
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
     * Renders the payment success page
     */
    @GetMapping("/success")
    public String success() {
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
