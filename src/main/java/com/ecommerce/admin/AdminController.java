package com.ecommerce.admin;

import com.ecommerce.order.Order;
import com.ecommerce.order.OrderRepository;
import com.ecommerce.order.PaymentStatus;
import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public AdminController(
            ProductRepository productRepository,
            OrderRepository orderRepository,
            UserRepository userRepository
    ) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/dashboard")
    public DashboardSummary getDashboardSummary(@RequestParam(defaultValue = "5") int lowStockThreshold) {
        List<Order> orders = orderRepository.findAll();
        List<Product> products = productRepository.findAll();

        BigDecimal salesTotal = orders.stream()
                .filter(order -> PaymentStatus.PAID.equals(order.getPaymentStatus()))
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long paidOrders = orders.stream().filter(order -> PaymentStatus.PAID.equals(order.getPaymentStatus())).count();
        long unpaidOrders = orders.stream().filter(order -> PaymentStatus.UNPAID.equals(order.getPaymentStatus())).count();
        long lowStockCount = products.stream().filter(product -> product.getAvailableStock() <= lowStockThreshold).count();

        return new DashboardSummary(
                salesTotal,
                orders.size(),
                paidOrders,
                unpaidOrders,
                userRepository.count(),
                productRepository.count(),
                lowStockCount
        );
    }

    @GetMapping("/products")
    public Page<Product> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean lowStockOnly,
            @RequestParam(defaultValue = "5") int lowStockThreshold,
            @RequestParam(defaultValue = "createdDesc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Specification<Product> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null && !keyword.isBlank()) {
                String likeKeyword = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), likeKeyword),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), likeKeyword)
                ));
            }

            if (category != null && !category.isBlank()) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(root.get("category")), category.trim().toLowerCase()));
            }

            if (Boolean.TRUE.equals(lowStockOnly)) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("stock"), lowStockThreshold));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), getProductSort(sort));
        Page<Product> productPage = productRepository.findAll(specification, pageable);
        productPage.getContent().forEach(this::initializeProductChildren);
        return productPage;
    }

    @GetMapping("/low-stock-products")
    public Page<Product> getLowStockProducts(
            @RequestParam(defaultValue = "5") int threshold,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        List<Product> lowStockProducts = productRepository.findAll().stream()
                .filter(product -> product.getAvailableStock() <= threshold)
                .sorted((left, right) -> Integer.compare(left.getAvailableStock(), right.getAvailableStock()))
                .toList();

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        int fromIndex = Math.min(safePage * safeSize, lowStockProducts.size());
        int toIndex = Math.min(fromIndex + safeSize, lowStockProducts.size());
        Pageable pageable = PageRequest.of(safePage, safeSize);
        return new PageImpl<>(lowStockProducts.subList(fromIndex, toIndex), pageable, lowStockProducts.size());
    }

    @GetMapping("/users")
    public Page<UserSummary> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Specification<User> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null && !keyword.isBlank()) {
                String likeKeyword = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), likeKeyword),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("username")), likeKeyword)
                ));
            }

            if (role != null && !role.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("role"), role.trim().toUpperCase()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.ASC, "email"));
        return userRepository.findAll(specification, pageable).map(this::toUserSummary);
    }

    @PutMapping("/users/{id:\\d+}/role")
    public UserSummary updateUserRole(@PathVariable Long id, @RequestParam String role) {
        String normalizedRole = role.trim().toUpperCase();
        if (!"USER".equals(normalizedRole) && !"ADMIN".equals(normalizedRole)) {
            throw new RuntimeException("Invalid role");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setRole(normalizedRole);
        return toUserSummary(userRepository.save(user));
    }

    @DeleteMapping("/users/{id:\\d+}")
    public String deleteUser(@PathVariable Long id) {
        userRepository.deleteById(id);
        return "User deleted";
    }

    private Sort getProductSort(String sort) {
        return switch (sort) {
            case "priceAsc" -> Sort.by(Sort.Direction.ASC, "price").and(Sort.by(Sort.Direction.ASC, "id"));
            case "priceDesc" -> Sort.by(Sort.Direction.DESC, "price").and(Sort.by(Sort.Direction.DESC, "id"));
            case "titleAsc" -> Sort.by(Sort.Direction.ASC, "title").and(Sort.by(Sort.Direction.ASC, "id"));
            case "stockAsc" -> Sort.by(Sort.Direction.ASC, "stock").and(Sort.by(Sort.Direction.ASC, "id"));
            case "stockDesc" -> Sort.by(Sort.Direction.DESC, "stock").and(Sort.by(Sort.Direction.DESC, "id"));
            default -> Sort.by(Sort.Direction.DESC, "id");
        };
    }

    private void initializeProductChildren(Product product) {
        product.getImages().size();
        product.getVariants().size();
    }

    private UserSummary toUserSummary(User user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }

    public record DashboardSummary(
            BigDecimal salesTotal,
            long totalOrders,
            long paidOrders,
            long unpaidOrders,
            long totalUsers,
            long totalProducts,
            long lowStockProducts
    ) {
    }

    public record UserSummary(Long id, String username, String email, String role) {
    }
}
