package com.foursales.ecommerce.service;

import com.foursales.ecommerce.constant.AppConstants;
import com.foursales.ecommerce.dto.ProductRequest;
import com.foursales.ecommerce.dto.ProductResponse;
import com.foursales.ecommerce.dto.ProductSyncEvent;
import com.foursales.ecommerce.entity.Product;
import com.foursales.ecommerce.exception.ResourceNotFoundException;
import com.foursales.ecommerce.mapper.ProductMapper;
import com.foursales.ecommerce.outbox.OutboxService;
import com.foursales.ecommerce.repository.search.ProductElasticsearchRepository;
import com.foursales.ecommerce.repository.jpa.ProductRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ProductService implements IProductService {

    private final ProductRepository productRepository;
    private final ProductElasticsearchRepository productSearchRepository;
    private final ProductMapper productMapper;
    private final OutboxService outboxService;
    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public Page<ProductResponse> getAllProductsPaginated(Pageable pageable) {
        Page<Product> products = productRepository.findAll(pageable);
        return products.map(productMapper::toResponse);
    }

    @Override
    public ProductResponse getProductById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return productMapper.toResponse(product);
    }

    @Override
    public ProductResponse createProduct(ProductRequest request) {
        Product product = productMapper.toEntity(request);
        Product savedProduct = productRepository.save(product);
        saveProductSyncEventToOutbox(savedProduct, "CREATED");
        return productMapper.toResponse(savedProduct);
    }

    @Override
    public ProductResponse updateProduct(UUID id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        productMapper.updateEntity(request, product);
        Product updatedProduct = productRepository.save(product);
        saveProductSyncEventToOutbox(updatedProduct, "UPDATED");
        return productMapper.toResponse(updatedProduct);
    }

    @Override
    public void deleteProduct(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        productRepository.delete(product);
        saveProductSyncEventToOutbox(product, "DELETED");
    }

    @Override
    @CircuitBreaker(name = "elasticsearch", fallbackMethod = "searchProductsFallback")
    public List<ProductResponse> searchProducts(String name, String category, BigDecimal priceMin,
            BigDecimal priceMax) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    if (name != null && !name.trim().isEmpty()) {
                        b.must(m -> m.match(match -> match
                                .field("name")
                                .query(name)
                                .fuzziness("AUTO")));
                    }

                    if (category != null && !category.trim().isEmpty()) {
                        b.filter(f -> f.term(t -> t
                                .field("category")
                                .value(category)));
                    }

                    if (priceMin != null && priceMax != null) {
                        b.filter(f -> f.range(r -> r
                                .field("price")
                                .gte(co.elastic.clients.json.JsonData.of(priceMin))
                                .lte(co.elastic.clients.json.JsonData.of(priceMax))));
                    }

                    b.filter(f -> f.range(r -> r
                            .field("stockQuantity")
                            .gt(co.elastic.clients.json.JsonData.of(0))));

                    return b;
                }))
                .build();

        SearchHits<Product> searchHits = elasticsearchOperations.search(query, Product.class);
        List<Product> products = searchHits.getSearchHits().stream()
                .map(org.springframework.data.elasticsearch.core.SearchHit::getContent)
                .toList();

        return productMapper.toResponseList(products);
    }

    // MySQL fallback: Priority-based filtering (cannot combine filters like
    // Elasticsearch)
    public List<ProductResponse> searchProductsFallback(String name, String category,
            BigDecimal priceMin, BigDecimal priceMax,
            Throwable throwable) {
        log.warn("Elasticsearch failed, using degraded MySQL fallback. Error: {}", throwable.getMessage());

        List<Product> products;

        if (name != null && !name.trim().isEmpty()) {
            products = productRepository.findByNameContainingIgnoreCase(name);
            log.warn("MySQL fallback: Using name filter only (category and price filters ignored)");
        } else if (category != null && !category.trim().isEmpty()) {
            products = productRepository.findByCategory(category);
            log.warn("MySQL fallback: Using category filter only (price filter ignored)");
        } else {
            if (priceMin != null && priceMax != null) {
                log.warn("MySQL fallback: Price range filter not supported, returning all in-stock products");
            }
            products = productRepository.findAllInStock();
        }

        return productMapper.toResponseList(products);
    }

    @Override
    public void syncProductToElasticsearch(Product product) {
        productSearchRepository.save(product);
    }

    private void saveProductSyncEventToOutbox(Product product, String eventType) {
        ProductSyncEvent event = ProductSyncEvent.builder()
                .productId(product.getId())
                .eventType(eventType)
                .eventTimestamp(LocalDateTime.now())
                .eventId(UUID.randomUUID().toString())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .category(product.getCategory())
                .stockQuantity(product.getStockQuantity())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();

        outboxService.saveEvent(
                "PRODUCT",
                product.getId().toString(),
                eventType,
                event,
                AppConstants.TOPIC_PRODUCT_SYNC);

        log.debug("Saved {} event to outbox for product: {}", eventType, product.getId());
    }
}