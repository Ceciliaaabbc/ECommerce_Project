# E-Commerce Backend

Spring Boot backend for a full-stack e-commerce application. The service exposes REST APIs for user authentication, product browsing, carts, checkout, orders, admin order management, reviews, product images, inventory reservations, and Stripe payment webhook handling.

## Tech Stack

- Java 17
- Spring Boot 3
- Spring Web, Spring Security, Spring Data JPA
- PostgreSQL
- Flyway database migrations
- Redis product caching
- JWT authentication and role-based authorization
- Stripe Checkout and webhook handling
- AWS S3 configuration for product images
- Maven
- Docker / Docker Compose
- GitHub Actions

## Running Locally

Create an environment file with the required settings, then run:

```bash
docker compose up --build
```

The backend starts on the configured `PORT`, defaulting to `8080`.

## Test Suite

Run all backend tests:

```bash
mvn clean test
```

The test suite includes:

- Unit tests for service-layer business logic, including `InventoryService`, `OrderService`, `OrderStateMachine`, `OrderAddressSnapshotService`, and `JwtUtil`.
- Controller slice tests for user, product, cart, and order endpoints.
- PostgreSQL integration tests powered by Testcontainers and Flyway migrations.
- API flow tests using MockMvc for:
  - user registration
  - user login
  - adding items to cart
  - checkout and pending-order creation
  - Stripe payment webhook status updates
  - inventory reservation and release
  - duplicate payment rejection
  - insufficient stock rejection
  - missing-token access rejection
  - non-admin access rejection for admin APIs

Stripe calls are mocked at the boundary in tests, so the suite validates backend business logic without contacting external payment services.

## CI

GitHub Actions runs backend tests on push and pull requests to `main`:

```yaml
mvn clean test
```
