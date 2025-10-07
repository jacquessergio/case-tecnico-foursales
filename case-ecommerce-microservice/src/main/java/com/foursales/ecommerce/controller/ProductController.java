package com.foursales.ecommerce.controller;

import com.foursales.ecommerce.config.SwaggerResponses;
import com.foursales.ecommerce.dto.PagedResponse;
import com.foursales.ecommerce.dto.ProductRequest;
import com.foursales.ecommerce.dto.ProductResponse;
import com.foursales.ecommerce.service.IProductService;
import com.foursales.ecommerce.util.PageableUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product management endpoints (API v1)")
@RequiredArgsConstructor
public class ProductController {

    private final IProductService productService;

    @Operation(summary = "List all products - paginated")
    @ApiResponse(responseCode = "200", description = "Product page returned successfully")
    @SwaggerResponses.BadRequest
    @SwaggerResponses.InternalError
    @GetMapping
    public ResponseEntity<PagedResponse<ProductResponse>> getAllProducts(Pageable pageable) {
        pageable = PageableUtils.applyPaginationRules(pageable);
        Page<ProductResponse> productsPage = productService.getAllProductsPaginated(pageable);
        PagedResponse<ProductResponse> pagedData = PagedResponse.of(productsPage);
        return ResponseEntity.ok(pagedData);
    }

    @Operation(summary = "Get product by ID")
    @ApiResponse(responseCode = "200", description = "Product encontrado")
    @SwaggerResponses.NotFound
    @SwaggerResponses.InternalError
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(
            @Parameter(description = "ID do produto") @PathVariable UUID id) {
        ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }

    @Operation(summary = "Create product", security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "200", description = "Product criado com sucesso")
    @SwaggerResponses.BadRequest
    @SwaggerResponses.Forbidden
    @SwaggerResponses.InternalError
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse product = productService.createProduct(request);
        return ResponseEntity.ok(product);
    }

    @Operation(summary = "Update product", security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "200", description = "Product atualizado com sucesso")
    @SwaggerResponses.BadRequest
    @SwaggerResponses.Forbidden
    @SwaggerResponses.NotFound
    @SwaggerResponses.InternalError
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> updateProduct(
            @Parameter(description = "ID do produto") @PathVariable UUID id,
            @Valid @RequestBody ProductRequest request) {

        ProductResponse updatedProduct = productService.updateProduct(id, request);
        return ResponseEntity.ok(updatedProduct);
    }

    @Operation(summary = "Delete product", security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(responseCode = "204", description = "Product deletado com sucesso")
    @SwaggerResponses.Forbidden
    @SwaggerResponses.NotFound
    @SwaggerResponses.InternalError
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@Parameter(description = "ID do produto") @PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Search products with optional filters using Elasticsearch")
    @ApiResponse(responseCode = "200", description = "List of products found", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProductResponse.class))))
    @SwaggerResponses.InternalError
    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> searchProducts(
            @Parameter(description = "Product name (with error tolerance)") @RequestParam(required = false) String name,
            @Parameter(description = "Product category") @RequestParam(required = false) String category,
            @Parameter(description = "Minimum price") @RequestParam(required = false) BigDecimal priceMin,
            @Parameter(description = "Maximum price") @RequestParam(required = false) BigDecimal priceMax) {

        List<ProductResponse> products = productService.searchProducts(name, category, priceMin, priceMax);
        return ResponseEntity.ok(products);
    }
}