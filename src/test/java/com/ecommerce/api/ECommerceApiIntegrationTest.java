package com.ecommerce.api;

import com.ecommerce.cart.CartItemRepository;
import com.ecommerce.order.Order;
import com.ecommerce.order.OrderItem;
import com.ecommerce.order.OrderItemRepository;
import com.ecommerce.order.OrderRepository;
import com.ecommerce.order.OrderStatus;
import com.ecommerce.order.PaymentStatus;
import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ECommerceApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void registerLoginAddToCartAndCheckout_shouldCreatePendingOrderAndReserveInventory() throws Exception {
        Product product = productRepository.saveAndFlush(product("Mechanical Keyboard", 5));
        String token = registerAndLogin("buyer@example.com", "password123");

        mockMvc.perform(post("/api/cart")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": 2
                                }
                                """.formatted(product.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Mechanical Keyboard"))
                .andExpect(jsonPath("$.quantity").value(2));

        Long addressId = createShippingAddress(token);

        try (MockedStatic<Session> sessionStatic = mockCheckoutSession("cs_test_checkout", "https://stripe.test/checkout")) {
            String body = mockMvc.perform(post("/api/orders/checkout")
                            .header("Authorization", bearer(token))
                            .param("shippingAddressId", addressId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.checkoutUrl").value("https://stripe.test/checkout"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            Long orderId = objectMapper.readTree(body).get("orderId").asLong();
            Order order = orderRepository.findById(orderId).orElseThrow();
            Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();

            assertThat(order.getUserEmail()).isEqualTo("buyer@example.com");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
            assertThat(order.isInventoryReserved()).isTrue();
            assertThat(order.getStripeSessionId()).isEqualTo("cs_test_checkout");
            assertThat(updatedProduct.getReservedStock()).isEqualTo(2);
            assertThat(orderItemRepository.findByOrderId(orderId)).hasSize(1);
            sessionStatic.verify(() -> Session.create(any(SessionCreateParams.class)));
        }
    }

    @Test
    void checkout_shouldRejectWhenStockIsNotEnough() throws Exception {
        Product product = productRepository.saveAndFlush(product("Low Stock Item", 1));
        String token = registerAndLogin("stock@example.com", "password123");

        mockMvc.perform(post("/api/cart")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": 2
                                }
                                """.formatted(product.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Not enough stock for product: Low Stock Item"));
    }

    @Test
    void protectedEndpoints_shouldRejectMissingTokenAndNonAdminAccess() throws Exception {
        String userToken = registerAndLogin("user@example.com", "password123");

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders/admin/search")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Access Denied"));
    }

    @Test
    void payOrder_shouldRejectDuplicatePayment() throws Exception {
        Product product = productRepository.saveAndFlush(product("Paid Item", 3));
        String token = registerAndLogin("paid@example.com", "password123");
        Order order = orderRepository.saveAndFlush(new Order("paid@example.com", BigDecimal.valueOf(25), OrderStatus.PROCESSING));
        order.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.saveAndFlush(order);
        orderItemRepository.saveAndFlush(new OrderItem(order.getId(), product.getId(), "Paid Item", BigDecimal.valueOf(25), 1));

        mockMvc.perform(post("/api/orders/%d/pay".formatted(order.getId()))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Order is already paid"));
    }

    @Test
    void adminRefund_shouldMarkPaidOrderRefunded() throws Exception {
        Product product = productRepository.saveAndFlush(product("Refund Item", 3));
        String adminToken = registerAndLogin("admin@example.com", "password123");
        promoteUserToAdmin("admin@example.com");
        adminToken = login("admin@example.com", "password123");
        Order order = orderRepository.saveAndFlush(new Order("buyer@example.com", BigDecimal.valueOf(25), OrderStatus.PROCESSING));
        order.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.saveAndFlush(order);
        orderItemRepository.saveAndFlush(new OrderItem(order.getId(), product.getId(), "Refund Item", BigDecimal.valueOf(25), 1));

        mockMvc.perform(post("/api/orders/%d/refund".formatted(order.getId()))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
    }

    @Test
    void cancelPayment_shouldReleaseReservedInventory() throws Exception {
        Product product = productRepository.saveAndFlush(product("Reserved Item", 4));
        String token = registerAndLogin("cancel@example.com", "password123");

        Long orderId = checkoutProduct(token, product.getId(), 2);

        mockMvc.perform(put("/api/orders/%d/cancel-payment".formatted(orderId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("CANCELLED"));

        Order cancelled = orderRepository.findById(orderId).orElseThrow();
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();

        assertThat(cancelled.isInventoryReserved()).isFalse();
        assertThat(updatedProduct.getReservedStock()).isZero();
        assertThat(updatedProduct.getStock()).isEqualTo(4);
    }

    @Test
    void stripeWebhook_shouldMarkOrderPaidDeductInventoryAndClearCart() throws Exception {
        Product product = productRepository.saveAndFlush(product("Webhook Item", 5));
        String token = registerAndLogin("webhook@example.com", "password123");
        Long orderId = checkoutProduct(token, product.getId(), 2);

        Event event = mock(Event.class);
        when(event.getType()).thenReturn("checkout.session.completed");

        try (MockedStatic<Webhook> webhookStatic = Mockito.mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            mockMvc.perform(post("/api/payments/webhook")
                            .header("Stripe-Signature", "test-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "data": {
                                        "object": {
                                          "id": "cs_test_paid",
                                          "payment_status": "paid",
                                          "client_reference_id": "%d"
                                        }
                                      }
                                    }
                                    """.formatted(orderId)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Order paid"));
        }

        Order paidOrder = orderRepository.findById(orderId).orElseThrow();
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();

        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(paidOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paidOrder.isInventoryReserved()).isFalse();
        assertThat(paidOrder.getStripeSessionId()).isEqualTo("cs_test_paid");
        assertThat(updatedProduct.getStock()).isEqualTo(3);
        assertThat(updatedProduct.getReservedStock()).isZero();
        assertThat(cartItemRepository.findByUserEmail("webhook@example.com")).isEmpty();
    }

    private Long checkoutProduct(String token, Long productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": %d
                                }
                                """.formatted(productId, quantity)))
                .andExpect(status().isOk());

        try (MockedStatic<Session> ignored = mockCheckoutSession("cs_test_%d".formatted(productId), "https://stripe.test/checkout")) {
            Long addressId = createShippingAddress(token);
            String body = mockMvc.perform(post("/api/orders/checkout")
                            .header("Authorization", bearer(token))
                            .param("shippingAddressId", addressId.toString()))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            return objectMapper.readTree(body).get("orderId").asLong();
        }
    }

    private MockedStatic<Session> mockCheckoutSession(String id, String url) throws Exception {
        Session session = mock(Session.class);
        when(session.getId()).thenReturn(id);
        when(session.getUrl()).thenReturn(url);

        MockedStatic<Session> sessionStatic = Mockito.mockStatic(Session.class);
        sessionStatic.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(session);
        return sessionStatic;
    }

    private String registerAndLogin(String email, String password) throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "Test User",
                                  "email": "%s",
                                  "password": "%s"
                                }
                        """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(content().string("Register successful"));

        return login(email, password, "USER");
    }

    private String login(String email, String password) throws Exception {
        return login(email, password, null);
    }

    private String login(String email, String password, String expectedRole) throws Exception {
        String response = mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(expectedRole == null ? result -> { } : jsonPath("$.role").value(expectedRole))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return json.get("token").asText();
    }

    private void promoteUserToAdmin(String email) {
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", email);
    }

    private Long createShippingAddress(String token) throws Exception {
        String response = mockMvc.perform(post("/api/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientName": "Test User",
                                  "phone": "1234567890",
                                  "country": "UK",
                                  "province": "Fife",
                                  "city": "St Andrews",
                                  "street": "1 Market Street",
                                  "postalCode": "KY16 9AA",
                                  "defaultAddress": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private Product product(String title, int stock) {
        Product product = new Product(title, "Test product", BigDecimal.valueOf(25), "https://example.com/image.png", stock);
        product.setCategory("Test");
        return product;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
