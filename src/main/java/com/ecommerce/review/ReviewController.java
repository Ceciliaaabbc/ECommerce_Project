package com.ecommerce.review;

import com.ecommerce.security.JwtUtil;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final JwtUtil jwtUtil;

    public ReviewController(ReviewRepository reviewRepository, JwtUtil jwtUtil) {
        this.reviewRepository = reviewRepository;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public List<Review> getReviewsByProductId(@RequestParam Long productId) {
        return reviewRepository.findByProductId(productId);
    }

    @PostMapping
    public Review addReview(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Review review
    ) {
        String token = authHeader.substring(7);
        String userEmail = jwtUtil.getEmailFromToken(token);

        review.setUserEmail(userEmail);
        review.setCreatedAt(LocalDateTime.now());

        return reviewRepository.save(review);
    }
}