/**
 * Adyen Advanced Flow Integration - Based on Sessions Flow Pattern
 */
document.addEventListener('DOMContentLoaded', () => {
    // DOM Elements
    const payButton = document.getElementById('pay-button');
    const enableRecurringCheckbox = document.getElementById('enableRecurring');
    const shopperReferenceGroup = document.getElementById('shopperReferenceGroup');
    const loadingElement = document.getElementById('loading');
    const errorElement = document.getElementById('error');
    const paymentContainer = document.getElementById('payment-container');
    const clientKey = document.querySelector('meta[name="client-key"]').getAttribute('content');

    // Modal elements
    const paymentModal = document.getElementById('payment-modal');
    const closeModalBtn = document.querySelector('.close-modal');

    // Close modal when clicking the close button
    closeModalBtn.addEventListener('click', () => {
        paymentModal.style.display = 'none';
    });

    // Close modal when clicking outside of it
    window.addEventListener('click', (event) => {
        if (event.target === paymentModal) {
            paymentModal.style.display = 'none';
        }
    });

    // Handle payment button click
    payButton.addEventListener('click', async () => {
        try {
            // Reset UI state
            errorElement.style.display = 'none';
            paymentContainer.innerHTML = '';
            loadingElement.style.display = 'block';

            // Get form values
            const amount = parseInt(document.getElementById('amount').value);
            const currency = document.getElementById('currency').value;
            const countryCode = document.getElementById('countryCode').value;
            const enableRecurring = document.getElementById('enableRecurring').checked;
            const shopperReference = document.getElementById('shopperReference').value;

            // Validate shopper reference is provided and meets minimum length
            if (!shopperReference) {
                throw new Error('Shopper Reference is required');
            }

            // Adyen requires shopperReference to be at least 3 characters
            if (shopperReference.length < 3) {
                errorElement.textContent = 'Shopper Reference must be at least 3 characters';
                errorElement.style.display = 'block';
                document.getElementById('shopperReference').focus();
                throw new Error('Shopper Reference must be at least 3 characters');
            }

            // Create payment methods request
            const paymentMethodsRequest = {
                amount: amount,
                currency: currency,
                countryCode: countryCode,
                shopperReference: shopperReference,
                enableRecurring: enableRecurring
            };

            // Call backend API to get payment methods
            const response = await fetch('/advanced/api/paymentMethods', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(paymentMethodsRequest)
            });

            if (!response.ok) {
                // Try to parse the error as JSON first
                try {
                    const errorData = await response.json();
                    console.error('Server response error:', response.status, errorData);
                    throw new Error(`Failed to get payment methods: ${response.status}`);
                } catch (jsonError) {
                    // If it's not valid JSON, handle as text
                    const errorText = await response.text();
                    console.error('Server response error:', response.status, errorText);
                    throw new Error(`Failed to get payment methods: ${response.status}`);
                }
            }

            // Parse payment methods data from response
            const paymentMethodsResponse = await response.json();
            console.log('Payment methods retrieved successfully');

            // Initialize Adyen checkout with payment methods data
            const { AdyenCheckout, Dropin } = window.AdyenWeb;

            const checkout = await AdyenCheckout({
                environment: 'test', // Using test environment
                clientKey: clientKey,
                locale: 'en_US',
                amount: {
                    currency: currency,
                    value: amount * 100
                },
                countryCode: countryCode, // Required for advanced flow
                // Use paymentMethodsResponse instead of session for advanced flow
                paymentMethodsResponse: paymentMethodsResponse,
                paymentMethodsConfiguration: {
                    card: {
                        hasHolderName: true,
                        holderNameRequired: true,
                        enableStoreDetails: true,
                        name: 'Credit or debit card',
                        billingAddressRequired: false
                    },
                    ideal: {
                        showImage: true
                    },
                    paypal: {
                        environment: 'test',
                        countryCode: countryCode,
                        amount: {
                            currency: currency,
                            value: amount * 100
                        }
                    }
                },
                onSubmit: async (state, component, actions) => {
                    try {
                        console.log('onSubmit', state);
                        
                        // Make a POST /payments request to your server
                        const paymentRequest = {
                            amount: {
                                currency: currency,
                                value: amount * 100 // Convert to minor units
                            },
                            paymentMethod: state.data.paymentMethod,
                            shopperReference: shopperReference,
                            countryCode: countryCode,
                            enableRecurring: enableRecurring,
                            browserInfo: state.data.browserInfo,
                            returnUrl: `${window.location.origin}/advanced/result`
                        };

                        const result = await fetch('/advanced/api/payments', {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json',
                            },
                            body: JSON.stringify(paymentRequest)
                        });

                        if (!result.ok) {
                            throw new Error('Payment request failed');
                        }

                        const paymentResult = await result.json();

                        // If the /payments request from your server fails, or if an unexpected error occurs
                        if (!paymentResult.resultCode) {
                            actions.reject();
                            return;
                        }

                        const {
                            resultCode,
                            action,
                            order,
                            donationToken
                        } = paymentResult;

                        // Handle final payment result if no action required
                        if (!action) {
                            handlePaymentResult(paymentResult);
                            return;
                        }

                        // You must call this, even if the result of the payment is unsuccessful
                        actions.resolve({
                            resultCode,
                            action,
                            order,
                            donationToken,
                        });
                    } catch (error) {
                        console.error("onSubmit error:", error);
                        actions.reject();
                    }
                },
                onAdditionalDetails: (state, component) => {
                    console.log('onAdditionalDetails', state);

                    // Check if this is a 3DS authentication result
                    if (state && state.data && state.data.details && state.data.details.threeDSResult) {
                        // Submit 3DS result to our backend
                        fetch('/advanced/api/payments/details', {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json',
                            },
                            body: JSON.stringify({
                                threeDSResult: state.data.details.threeDSResult,
                                paymentData: state.data.paymentData
                            })
                        })
                        .then(response => {
                            if (!response.ok) {
                                throw new Error('Failed to process 3DS details');
                            }
                            return response.json();
                        })
                        .then(data => {
                            console.log('3DS verification complete:', data);
                            // Make sure we have all the important data
                            if (!data.pspReference) {
                                console.warn('3DS response missing PSP reference');
                            }
                            if (!data.merchantReference) {
                                console.warn('3DS response missing merchant reference');
                            }

                            // Store the result and handle UI updates
                            // We store this before redirect so it's available when the success page loads
                            data.flowType = 'advanced';
                            sessionStorage.setItem('paymentResult', JSON.stringify(data));
                            console.log('Stored payment result in sessionStorage before redirect');

                            // For 3DS, we use client-side storage because the server doesn't have context
                            // Redirect based on result code
                            const resultCodeUpper = data.resultCode ? data.resultCode.toUpperCase() : '';
                            if (resultCodeUpper === 'AUTHORISED') {
                                console.log('Payment authorized, redirecting to success page');
                                window.location.href = '/success';
                            } else if (resultCodeUpper === 'PENDING' || resultCodeUpper === 'RECEIVED') {
                                window.location.href = '/pending';
                            } else {
                                sessionStorage.setItem('paymentError', 'Payment failed: ' + data.resultCode);
                                window.location.href = '/failed';
                            }
                        })
                        .catch(error => {
                            console.error('Error handling 3DS details:', error);
                            sessionStorage.setItem('paymentError', error.message || 'Error processing 3DS authentication');
                            window.location.href = '/failed';
                        });
                    }
                },
                onError: (error) => {
                    console.error('Payment error:', error);
                    // Store error message for failed page
                    sessionStorage.setItem('paymentError', error.message || 'Something went wrong with the payment');
                    paymentModal.style.display = 'none';
                    window.location.href = '/failed';
                }
            });

            // Mount the drop-in component
            const dropinConfiguration = {
                showPayButton: true,
                amount: {
                    value: amount,
                    currency: currency
                }
            };
            const dropin = new Dropin(checkout, dropinConfiguration).mount(paymentContainer);

            // Show the payment modal
            paymentModal.style.display = 'block';

        } catch (error) {
            console.error('Error:', error);
            showError(error.message || 'An unexpected error occurred');
        } finally {
            loadingElement.style.display = 'none';
        }
    });


    /**
     * Handle final payment result
     */
    function handlePaymentResult(result) {
        console.log('Payment result:', result);
        // Add flow type to result data
        result.flowType = 'advanced';
        sessionStorage.setItem('paymentResult', JSON.stringify(result));
        paymentModal.style.display = 'none';

        // Redirect based on result code - case insensitive comparison
        const resultCodeUpper = result.resultCode ? result.resultCode.toUpperCase() : '';
        if (resultCodeUpper === 'AUTHORISED') {
            window.location.href = '/success';
        } else if (resultCodeUpper === 'PENDING' || resultCodeUpper === 'RECEIVED') {
            // Store pending info for pending page
            sessionStorage.setItem('pendingPayment', JSON.stringify(result));
            window.location.href = '/pending';
        } else {
            // Store error message for failed page
            sessionStorage.setItem('paymentError', 'Payment failed: ' + result.resultCode);
            window.location.href = '/failed';
        }
    }

    /**
     * Display error message
     */
    function showError(message) {
        errorElement.textContent = message;
        errorElement.style.display = 'block';
    }
});
