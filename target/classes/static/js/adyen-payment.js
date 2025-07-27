/**
 * Adyen Web Payment Integration
 */
document.addEventListener('DOMContentLoaded', () => {
    // DOM Elements

    // DOM Elements
    const payButton = document.getElementById('pay-button');
    const enableRecurringCheckbox = document.getElementById('enableRecurring');
    const shopperReferenceGroup = document.getElementById('shopperReferenceGroup');
    const loadingElement = document.getElementById('loading');
    const errorElement = document.getElementById('error');
    const paymentContainer = document.getElementById('payment-container');
    const clientKey = document.querySelector('meta[name="client-key"]').getAttribute('content');

    // Check for redirect result in URL
    const urlParams = new URLSearchParams(window.location.search);
    const redirectResult = urlParams.get('redirectResult');

    // If we have a redirectResult but we're not on the success page, handle it with the API
    if (redirectResult && !window.location.pathname.includes('/success')) {
        handleRedirectResult(redirectResult);
    }

    // We now always show the shopper reference field
    // The checkbox only controls the recurring processing model

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

            // Create payment session request - always include shopper reference
            const sessionRequest = {
                amount: amount,
                currency: currency,
                countryCode: countryCode,
                returnUrl: window.location.origin + '/success',
                enableRecurring: enableRecurring,
                shopperReference: shopperReference
            };

            // Call backend API to create session
            const response = await fetch('/api/sessions', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(sessionRequest)
            });

            if (!response.ok) {
                // Try to parse the error as JSON first
                try {
                    const errorData = await response.json();
                    console.error('Server response error:', response.status, errorData);
                    throw new Error(`Failed to create payment session: ${response.status}`);
                } catch (jsonError) {
                    // If it's not valid JSON, handle as text
                    const errorText = await response.text();
                    console.error('Server response error:', response.status, errorText);
                    throw new Error(`Failed to create payment session: ${response.status}`);
                }
            }

            // Parse session data from response
            const sessionData = await response.json();
            console.log('Session created successfully:', sessionData.sessionId);

            // Initialize Adyen checkout with session data

            const { AdyenCheckout, Dropin } = window.AdyenWeb;

            const checkout = await AdyenCheckout({
                environment: 'test', // Using test environment
                clientKey: clientKey,
                locale: 'en_US',
                session: {
                    id: sessionData.sessionId,
                    sessionData: sessionData.sessionData
                },
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
                onRedirect: (data, component) => {
                    console.log('onRedirect', data);
                    // Close the modal when redirecting
                    paymentModal.style.display = 'none';
                    // Most redirect methods use window.location, handled by the SDK
                },
                onAdditionalDetails: (state, component) => {
                    console.log('onAdditionalDetails', state);

                    // Check if this is a 3DS authentication result
                    if (state && state.data && state.data.details && state.data.details.threeDSResult) {
                        // Submit 3DS result to our backend
                        fetch('/api/payments/3DSDetails', {
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
                onPaymentCompleted: (result) => {
                    console.log('Payment completed:', result);
                    sessionStorage.setItem('paymentResult', JSON.stringify(result));
                    console.log(sessionData);
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
                },
                onPaymentFailed: (result, component) => {
                    console.error('Payment failed:', result);
                    paymentModal.style.display = 'none';

                    // Store details for failed page
                    sessionStorage.setItem('paymentError', 'Payment failed: ' + 
                        (result.resultCode || result.refusalReason || 'Unknown reason'));

                    // Redirect to failed page
                    window.location.href = '/failed';
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
            const dropinConfiguration = {};
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
     * Display error message
     */
    function showError(message) {
        errorElement.textContent = message;
        errorElement.style.display = 'block';
    }

    /**
     * Handle redirect result from Adyen redirect payment methods
     */
    function handleRedirectResult(redirectResult) {
        try {
            loadingElement.style.display = 'block';
            errorElement.style.display = 'none';

            console.log('Processing redirect result');

            // Call backend API to process the redirect result
            fetch('/api/payments/details', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    redirectResult: redirectResult
                })
            })
            .then(response => {
                if (!response.ok) {
                    throw new Error('Failed to process redirect result');
                }
                return response.json();
            })
            .then(data => {
                console.log('Redirect flow complete:', data);
                // Make sure we have all the important data
                if (!data.pspReference) {
                    console.warn('Redirect response missing PSP reference');
                }
                if (!data.merchantReference) {
                    console.warn('Redirect response missing merchant reference');
                }
                if (!data.additionalData) {
                    console.warn('Redirect response missing additional data');
                }

                // Store the result and handle UI updates
                // We store this before redirect so it's available when the success page loads
                console.log('About to store payment result from redirect flow:', data);
                console.log('Data has additionalData?', data.additionalData ? 'Yes' : 'No');
                if (data.additionalData) {
                    console.log('additionalData keys:', Object.keys(data.additionalData));
                }

                sessionStorage.setItem('paymentResult', JSON.stringify(data));
                console.log('Stored payment result in sessionStorage before redirect');
                console.log('sessionStorage content:', sessionStorage.getItem('paymentResult'));

                // Verify the data was stored correctly
                try {
                    const stored = JSON.parse(sessionStorage.getItem('paymentResult'));
                    console.log('Verified stored data has additionalData:', stored.additionalData ? 'Yes' : 'No');
                } catch (e) {
                    console.error('Error parsing stored data:', e);
                }

                // Redirect based on result code
                const resultCodeUpper = data.resultCode ? data.resultCode.toUpperCase() : '';
                if (resultCodeUpper === 'AUTHORISED' || resultCodeUpper === 'AUTHENTICATED') {
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
                console.error('Error processing redirect result:', error);
                showError(error.message || 'Failed to process payment redirect');
                loadingElement.style.display = 'none';
            });
        } catch (error) {
            console.error('Error handling redirect result:', error);
            showError(error.message || 'Failed to process payment redirect');
            loadingElement.style.display = 'none';
        }
    }
});
