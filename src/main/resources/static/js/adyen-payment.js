/**
 * Adyen Web Payment Integration
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

    // Check for redirect result in URL
    const urlParams = new URLSearchParams(window.location.search);
    const redirectResult = urlParams.get('redirectResult');

    // If we have a redirectResult but we're not on the success page, handle it with the API
    if (redirectResult && !window.location.pathname.includes('/success')) {
        handleRedirectResult(redirectResult);
    }

    // Toggle shopper reference field visibility based on recurring checkbox
    enableRecurringCheckbox.addEventListener('change', () => {
        shopperReferenceGroup.style.display = enableRecurringCheckbox.checked ? 'block' : 'none';
    });

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

            // Create payment session request
            const sessionRequest = {
                amount: amount,
                currency: currency,
                countryCode: countryCode,
                returnUrl: window.location.origin + '/success',
                enableRecurring: enableRecurring,
                shopperReference: enableRecurring ? shopperReference : undefined
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
                const errorText = await response.text();
                console.error('Server response error:', response.status, errorText);
                throw new Error(`Failed to create payment session: ${response.status}`);
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
                    // Handle additional details if needed
                },
                onPaymentCompleted: (result) => {
                    console.log('Payment completed:', result);
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
    async function handleRedirectResult(redirectResult) {
        try {
            loadingElement.style.display = 'block';
            errorElement.style.display = 'none';

            // Create request payload - using our RedirectDetailsRequest model
            const detailsRequest = {
                redirectResult: redirectResult
            };

            // Submit to backend
            const response = await fetch('/api/payments/details', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(detailsRequest)
            });

            if (!response.ok) {
                throw new Error(`Error processing redirect result: ${response.status}`);
            }

            // Get response data
            const resultData = await response.json();
            console.log('Payment details result:', resultData);

            // Store result in session storage
            sessionStorage.setItem('paymentResult', JSON.stringify(resultData));

            // Redirect based on result - case insensitive comparison
            const resultCodeUpper = resultData.resultCode ? resultData.resultCode.toUpperCase() : '';
            if (resultCodeUpper === 'AUTHORISED' || resultCodeUpper === 'AUTHORISED') {
                window.location.href = '/success';
            } else if (resultCodeUpper === 'PENDING' || resultCodeUpper === 'RECEIVED') {
                window.location.href = '/pending';
            } else {
                window.location.href = '/failed';
            }

        } catch (error) {
            console.error('Error handling redirect result:', error);
            showError(error.message || 'Failed to process payment redirect');
            loadingElement.style.display = 'none';
        }
    }
});
