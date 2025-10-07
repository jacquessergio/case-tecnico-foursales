package com.foursales.ecommerce.service;

import com.foursales.ecommerce.dto.ProductRequest;
import com.foursales.ecommerce.dto.ProductResponse;
import com.foursales.ecommerce.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface IProductService {

    Page<ProductResponse> getAllProductsPaginated(Pageable pageable);

    ProductResponse getProductById(UUID id);

    ProductResponse createProduct(ProductRequest request);

    ProductResponse updateProduct(UUID id, ProductRequest request);

    void deleteProduct(UUID id);

    List<ProductResponse> searchProducts(String name, String category, BigDecimal priceMin, BigDecimal priceMax);

    void syncProductToElasticsearch(Product product);
}
