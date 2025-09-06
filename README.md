# Adyen Web Demo

A comprehensive Spring Boot application demonstrating Adyen payment integration with both **Sessions Flow** and **Advanced Flow** using the Adyen Web SDK v6.6.0.

## Features

### 🚀 Dual Payment Flow Support
- **Sessions Flow**: Session-based payments with simplified integration
- **Advanced Flow**: Direct payment methods API with full control
- **Flow Selection UI**: Choose between integration approaches

### 💳 Payment Capabilities
- Adyen Drop-in Component integration
- Support for recurring payments and stored payment methods
- 3DS authentication (both native and redirect)
- Comprehensive payment method support
- Real-time payment status handling

### 🛠 Technical Features
- Clean separation of concerns (MVC pattern)
- Modern, responsive UI with Adyen green theme
- Modal-based payment experience
- Comprehensive logging with full JSON serialization
- Production-ready error handling and validation

## Technology Stack

- **Java 17**
- **Spring Boot 3.2.4**
- **Adyen Java API Library 39.0.0**
- **Adyen Web SDK 6.6.0**
- **Thymeleaf** templates
- **Lombok** for boilerplate reduction

## Project Structure

```
src/main/java/com/example/adyenwebdemo/
├── config/          # Application configuration
├── controller/      # REST and MVC controllers
│   ├── SessionsFlowController.java
│   └── AdvancedFlowController.java
├── model/           # Data models/DTOs
├── service/         # Business logic services
│   ├── SessionsFlowService.java
│   └── AdvancedFlowService.java

src/main/resources/
├── static/          # Static resources (CSS, JS)
│   └── js/
│       ├── sessions-flow.js
│       └── advanced-flow.js
├── templates/       # Thymeleaf templates
│   ├── flow-selection.html
│   ├── sessions-flow.html
│   ├── advanced-flow.html
│   ├── success.html
│   ├── failed.html
│   └── pending.html
└── application.properties
```

## Quick Start

### 1. Configure Adyen Credentials

Update `application.properties` with your Adyen API credentials:

```properties
adyen.api.key=YOUR_API_KEY
adyen.merchant.account=YOUR_MERCHANT_ACCOUNT
adyen.client.key=YOUR_CLIENT_KEY
adyen.hmac.key=YOUR_HMAC_KEY
adyen.api.environment=TEST
```

### 2. Build and Run

```bash
# Build the application
./mvnw clean package

# Run the application
./mvnw spring-boot:run
```

### 3. Access the Application

Navigate to `http://localhost:8080` to access the flow selection page.

## Payment Flows

### 🔄 Sessions Flow
**Best for**: Simplified integration with minimal backend logic

1. User configures payment parameters (amount, currency, country)
2. Backend creates Adyen payment session via `/sessions` API
3. Frontend initializes Drop-in component with session data
4. Adyen handles payment processing and returns results
5. Backend retrieves final payment status via session result API

**Key Benefits**:
- Simplified backend implementation
- Adyen handles most payment logic
- Built-in 3DS and redirect handling

### ⚡ Advanced Flow
**Best for**: Full control over payment process and custom logic

1. User configures payment parameters
2. Backend fetches available payment methods via `/paymentMethods` API
3. Frontend displays payment methods using Drop-in component
4. User selects payment method and provides details
5. Backend processes payment via `/payments` API
6. Handle additional actions (3DS, redirects) via `/payments/details` API

**Key Benefits**:
- Full control over payment flow
- Custom business logic integration
- Detailed payment method handling
- Stored payment method support

## API Endpoints

### Sessions Flow
- `GET /sessions-flow` - Payment form page
- `POST /api/sessions-flow/create-session` - Create payment session
- `POST /api/sessions-flow/session-result` - Get session result
- `POST /api/sessions-flow/payment-details` - Handle redirect results

### Advanced Flow
- `GET /advanced-flow` - Payment form page
- `POST /api/advanced-flow/payment-methods` - Get available payment methods
- `POST /api/advanced-flow/payments` - Process payment
- `POST /api/advanced-flow/payment-details` - Handle additional payment actions

## Logging

The application provides comprehensive logging for all Adyen API interactions:

```
=== ADYEN SESSIONS API REQUEST ===
Merchant Account: YourMerchantAccount
Amount: 1000 EUR
Full Request JSON: {...}

=== ADYEN SESSIONS API RESPONSE ===
Session ID: CS123...
Full Response JSON: {...}
```

## Configuration Options

### Recurring Payments
- Enable via `enableRecurring` parameter
- Provide `shopperReference` for customer identification
- Automatic stored payment method detection

### 3DS Authentication
- **Sessions Flow**: Configured to prefer native 3DS
- **Advanced Flow**: Configurable 3DS behavior
- Support for both challenge and frictionless flows

## Development

- Uses Spring Boot DevTools for hot reloading
- Comprehensive error handling and validation
- Clean separation between Sessions and Advanced flows
- Production-ready logging and monitoring

## Production Considerations

- Implement proper HMAC signature validation for webhooks
- Configure appropriate security headers
- Set up proper logging and monitoring
- Use environment-specific configuration
- Implement rate limiting and request validation

## Resources

- [Adyen Sessions Flow Documentation](https://docs.adyen.com/online-payments/web-drop-in/sessions-flow/)
- [Adyen Advanced Flow Documentation](https://docs.adyen.com/online-payments/web-drop-in/advanced-flow/)
- [Adyen Web SDK Documentation](https://docs.adyen.com/online-payments/web-drop-in/)
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
