package com.ecommerce.product;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class ProductCacheService {

    private static final String ALL_PRODUCTS_KEY = "products:all";
    private static final String PRODUCT_DETAIL_KEY_PREFIX = "products:";

    private final RedisTemplate<String, Object> redisTemplate;

    public ProductCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void cacheAllProducts(List<Product> products) {
        redisTemplate.opsForValue().set(
                ALL_PRODUCTS_KEY,
                products,
                Duration.ofMinutes(10)
        );
    }

    public Object getAllProductsFromCache() {
        return getFromCache(ALL_PRODUCTS_KEY);
    }

    public void cacheProduct(Product product) {
        redisTemplate.opsForValue().set(
                PRODUCT_DETAIL_KEY_PREFIX + product.getId(),
                product,
                Duration.ofMinutes(10)
        );
    }

    public Object getProductFromCache(Long id) {
        return getFromCache(PRODUCT_DETAIL_KEY_PREFIX + id);
    }

    public void clearAllProductsCache() {
        redisTemplate.delete(ALL_PRODUCTS_KEY);
    }

    public void clearProductCache(Long id) {
        redisTemplate.delete(PRODUCT_DETAIL_KEY_PREFIX + id);
    }

    public void clearProductCaches(Long id) {
        clearAllProductsCache();
        clearProductCache(id);
    }

    private Object getFromCache(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (RuntimeException ex) {
            redisTemplate.delete(key);
            return null;
        }
    }
}
