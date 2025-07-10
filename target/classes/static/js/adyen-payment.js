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

    // Toggle shopper reference field visibility based on recurring checkbox
    enableRecurringCheckbox.addEventListener('change', () => {
        shopperReferenceGroup.style.display = enableRecurringCheckbox.checked ? 'block' : 'none';
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
                    }
                },
                onPaymentCompleted: (result) => {
                    console.log('Payment completed:', result);
                    sessionStorage.setItem('paymentResult', JSON.stringify(result));
                    window.location.href = '/success';
                },
                onError: (error) => {
                    console.error('Payment error:', error);
                    showError(error.message || 'Something went wrong with the payment');
                    sessionStorage.setItem('paymentResult', JSON.stringify({error: error.message}));
                }
            });

            // Mount the drop-in component
            const dropinConfiguration = {};
            const dropin = new Dropin(checkout, dropinConfiguration).mount(paymentContainer);

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
});
