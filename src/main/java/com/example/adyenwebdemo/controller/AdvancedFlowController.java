package com.example.adyenwebdemo.controller;

import com.adyen.service.exception.ApiException;
import com.example.adyenwebdemo.model.SessionsFlowRequest;
import com.example.adyenwebdemo.service.AdvancedFlowService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/advanced")
public class AdvancedFlowController {

    private final AdvancedFlowService advancedFlowService;

    @Value("${adyen.client.key}")
    private String clientKey;

    /**
     * Renders the advanced flow payment form
     */
    @GetMapping("")
    public String advancedFlow(Model model) {
        model.addAttribute("clientKey", clientKey);
        return "advanced-flow";
    }

    /**
     * Get available payment methods for advanced flow
     */
    @PostMapping("/api/paymentMethods")
    @ResponseBody
    public ResponseEntity<?> getPaymentMethods(@RequestBody SessionsFlowRequest paymentRequest) {
        try {
            log.info("Getting payment methods for advanced flow: {}", paymentRequest);
            Map<String, Object> response = advancedFlowService.getPaymentMethods(paymentRequest);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting payment methods", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Make payment using advanced flow
     */
    @PostMapping("/api/payments")
    @ResponseBody
    public ResponseEntity<?> makePayment(
            @RequestBody Map<String, Object> paymentData,
            HttpServletRequest request) {
        try {
            log.info("Making payment with advanced flow: {}", paymentData);
            
            // Add return URL if not provided
            if (!paymentData.containsKey("returnUrl")) {
                String returnUrl = request.getScheme() + "://" + 
                        request.getServerName() + ":" + request.getServerPort() + "/result";
                paymentData.put("returnUrl", returnUrl);
            }

            Map<String, Object> response = advancedFlowService.makePayment(paymentData);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error making payment", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Handle payment details for advanced flow
     */
    @PostMapping("/api/payments/details")
    @ResponseBody
    public ResponseEntity<?> submitPaymentDetails(@RequestBody Map<String, Object> detailsData) {
        try {
            log.info("Submitting payment details for advanced flow: {}", detailsData);
            Map<String, Object> response = advancedFlowService.submitPaymentDetails(detailsData);
            return ResponseEntity.ok(response);
        } catch (IOException | ApiException e) {
            log.error("Error submitting payment details", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Handle payment result page for advanced flow - now redirects to unified result pages
     */
    @GetMapping("/result")
    public String handleResult(
            @RequestParam(value = "redirectResult", required = false) String redirectResult,
            Model model) {
        
        if (redirectResult != null && !redirectResult.isEmpty()) {
            try {
                log.info("Received redirect result for advanced flow");
                // Process redirect result through service
                Map<String, Object> result = advancedFlowService.submitPaymentDetails(
                    Map.of("redirectResult", redirectResult)
                );
                
                // Add flow type to result
                result.put("flowType", "advanced");
                model.addAttribute("paymentResult", result);
                
                // Redirect based on result code
                String resultCode = (String) result.get("resultCode");
                if (resultCode != null) {
                    if (resultCode.equals("Authorised")) {
                        return "success";
                    } else if (resultCode.equals("Pending") || resultCode.equals("Received")) {
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
     * Advanced flow success page
     */
    @GetMapping("/success")
    public String advancedSuccess() {
        return "advanced-success";
    }

    /**
     * Advanced flow failed page
     */
    @GetMapping("/failed")
    public String advancedFailed() {
        return "advanced-failed";
    }
}
