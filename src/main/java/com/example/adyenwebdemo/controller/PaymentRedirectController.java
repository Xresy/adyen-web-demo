package com.example.adyenwebdemo.controller;

import com.adyen.service.exception.ApiException;
import com.example.adyenwebdemo.model.RedirectDetailsRequest;
import com.example.adyenwebdemo.model.RedirectDetailsResponse;
import com.example.adyenwebdemo.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * REST controller that handles API endpoints for Adyen payment flows
 * This controller specifically handles redirect results from Adyen
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentRedirectController {

    private final PaymentService paymentService;

    /**
     * Handle payment details submission from frontend
     * This endpoint processes the redirectResult from Adyen redirect payment methods
     */
    @PostMapping("/details")
    public ResponseEntity<RedirectDetailsResponse> paymentDetails(
            @RequestBody RedirectDetailsRequest detailsRequest) {
        try {
            log.info("Submitting payment details: {}", detailsRequest);
            RedirectDetailsResponse response = paymentService.submitPaymentDetails(detailsRequest);
            log.info("Details processed with result: {}", response.getResultCode());
            return ResponseEntity.ok(response);
        } catch (IOException | ApiException e) {
            log.error("Error submitting payment details", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
