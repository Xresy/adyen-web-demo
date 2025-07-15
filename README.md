# Adyen Web Demo - /sessions Flow
# Adyen Web Demo

A Spring Boot application demonstrating Adyen payment integration using the Adyen Web SDK v6.6.0.

## Features

- Adyen Drop-in Component integration
- Session-based payment flow
- Support for recurring payments
- Clean separation of concerns (MVC pattern)
- Modern, responsive UI with Adyen green theme
- Modal-based drop-in payment experience

## Technology Stack

- Java 17
- Spring Boot 3.2.4
- Adyen Java API Library 20.0.0
- Adyen Web SDK 6.6.0
- Thymeleaf templates
- Lombok

## Project Structure

- `src/main/java/com/example/adyenwebdemo`
  - `config` - Application configuration
  - `controller` - REST and MVC controllers
  - `model` - Data models/DTOs
  - `service` - Business logic services

- `src/main/resources`
  - `static` - Static resources (CSS, JS)
  - `templates` - Thymeleaf templates
  - `application.properties` - Application configuration

## Setup

1. Configure your Adyen API credentials in `application.properties`:

```properties
adyen.api.key=YOUR_API_KEY
adyen.merchant.account=YOUR_MERCHANT_ACCOUNT
adyen.client.key=YOUR_CLIENT_KEY
adyen.hmac.key=YOUR_HMAC_KEY
```

2. Build the application:

```bash
./mvnw clean package
```

3. Run the application:

```bash
./mvnw spring-boot:run
```

4. Access the application at `http://localhost:8080`

## Payment Flow

1. User selects payment amount, currency, and country
2. Optional: Enable recurring payments and provide shopper reference
3. Click "Proceed to Payment" button
4. Backend creates Adyen payment session
5. Frontend initializes Adyen Drop-in component with session data in a modal window
6. User selects payment method and completes payment
7. For redirect payment methods (iDEAL, Sofort, etc.):
   - User is redirected to payment provider's page
   - After completion, user is redirected back with a `redirectResult` parameter
   - The application handles this parameter via `/payments/details` endpoint
   - Payment result is displayed based on the response
8. For non-redirect methods, the result is handled directly within the drop-in component
9. Final result shown on success/failure pages

## Development

This project uses Spring Boot DevTools for hot reloading during development.

## Resources

- [Adyen Documentation](https://docs.adyen.com/)
- [Adyen Web SDK Documentation](https://docs.adyen.com/online-payments/web-drop-in/)
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
This is a simple Java Spring Boot application that demonstrates how to integrate with Adyen's checkout API using the /sessions flow.

## Prerequisites

- Java 17 or higher
- Maven
- An Adyen test account

## Configuration

Before running the application, you need to configure your Adyen API credentials in the `application.properties` file:

```properties
adyen.api.apiKey=YOUR_API_KEY
adyen.api.merchantAccount=YOUR_MERCHANT_ACCOUNT
adyen.api.environment=TEST
adyen.api.clientKey=YOUR_CLIENT_KEY
adyen.hmac.key=YOUR_HMAC_KEY
```

Replace the placeholders with your actual Adyen API credentials.

## Running the Application

```bash
mvn spring-boot:run
```

The application will be available at http://localhost:8080

## How It Works

1. The application displays a form where you can enter payment details.
2. When you click the "Initialize Payment" button, the application makes a request to the Adyen API to create a payment session.
3. Adyen returns a session ID and session data, which are used to initialize the Drop-in UI component.
4. The Drop-in component displays the available payment methods and handles the payment flow.
5. After the payment is processed, the application redirects to either the success or failure page.

## Implementation Details

- The application uses the Adyen Java API library to communicate with the Adyen API.
- The `/api/payments/sessions` endpoint creates a payment session using the Adyen API.
- The frontend uses the Adyen Web SDK to render the payment form and handle the payment flow.
- The application includes a webhook endpoint for handling payment status updates from Adyen.

## Notes

- This is a demo application and should not be used in production without proper security measures.
- In a production environment, you should implement proper error handling, logging, and security measures.
- The application does not implement HMAC signature validation for webhooks, which is recommended for production use.
