package com.foursales.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foursales.ecommerce.config.TestConfig;
import com.foursales.ecommerce.dto.ProductRequest;
import com.foursales.ecommerce.dto.ProductResponse;
import com.foursales.ecommerce.entity.User;
import com.foursales.ecommerce.enums.UserRole;
import com.foursales.ecommerce.exception.ResourceNotFoundException;
import com.foursales.ecommerce.repository.jpa.UserRepository;
import com.foursales.ecommerce.service.IProductService;
import com.foursales.ecommerce.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.elasticsearch.uris=",
    "spring.data.elasticsearch.repositories.enabled=false",
    "management.health.elasticsearch.enabled=false"
})
@Import(TestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IProductService productService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private ProductRequest productRequest;
    private ProductResponse productResponse;
    private UUID productId;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        productRequest = new ProductRequest("Test Product", "Description", new BigDecimal("100.00"), "Electronics", 10);
        productResponse = ProductResponse.builder()
                .id(productId)
                .name("Test Product")
                .description("Description")
                .price(new BigDecimal("100.00"))
                .category("Electronics")
                .stockQuantity(10)
                .build();

        User adminUser = new User("Admin User", "admin@test.com", passwordEncoder.encode("password"), UserRole.ADMIN);
        adminUser = userRepository.save(adminUser);
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(adminUser, null, adminUser.getAuthorities());
        adminToken = jwtTokenProvider.generateToken(adminAuth);

        User regularUser = new User("Regular User", "user@test.com", passwordEncoder.encode("password"), UserRole.USER);
        regularUser = userRepository.save(regularUser);
        Authentication userAuth = new UsernamePasswordAuthenticationToken(regularUser, null, regularUser.getAuthorities());
        userToken = jwtTokenProvider.generateToken(userAuth);
    }

    @Test
    @DisplayName("Should get all products paginated")
    void shouldGetAllProductsPaginated() throws Exception {
        Page<ProductResponse> page = new PageImpl<>(List.of(productResponse));
        when(productService.getAllProductsPaginated(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Test Product"))
                .andExpect(jsonPath("$.content[0].price").value(100.00));

        verify(productService).getAllProductsPaginated(any());
    }

    @Test
    @DisplayName("Should get product by id")
    void shouldGetProductById() throws Exception {
        when(productService.getProductById(productId)).thenReturn(productResponse);

        mockMvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Product"))
                .andExpect(jsonPath("$.price").value(100.00));

        verify(productService).getProductById(productId);
    }

    @Test
    @DisplayName("Should return 404 when product not found")
    void shouldReturn404WhenProductNotFound() throws Exception {
        when(productService.getProductById(productId))
                .thenThrow(new ResourceNotFoundException("Product not found"));

        mockMvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(status().isNotFound());

        verify(productService).getProductById(productId);
    }

    @Test
    @DisplayName("Should search products with filters")
    void shouldSearchProductsWithFilters() throws Exception {
        when(productService.searchProducts(anyString(), anyString(), any(), any()))
                .thenReturn(List.of(productResponse));

        mockMvc.perform(get("/api/v1/products/search")
                        .param("name", "Test")
                        .param("category", "Electronics")
                        .param("minPrice", "50.00")
                        .param("maxPrice", "150.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[0].name").value("Test Product"));

        verify(productService).searchProducts(eq("Test"), eq("Electronics"), any(), any());
    }

    @Test
    @DisplayName("Should search products without filters")
    void shouldSearchProductsWithoutFilters() throws Exception {
        when(productService.searchProducts(any(), any(), any(), any()))
                .thenReturn(List.of(productResponse));

        mockMvc.perform(get("/api/v1/products/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[0].name").value("Test Product"));

        verify(productService).searchProducts(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should create product as admin")
    void shouldCreateProductAsAdmin() throws Exception {
        when(productService.createProduct(any())).thenReturn(productResponse);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Product"));

        verify(productService).createProduct(any());
    }

    @Test
    @DisplayName("Should return 403 when non-admin tries to create product")
    void shouldReturn403WhenNonAdminTriesToCreateProduct() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isForbidden());

        verify(productService, never()).createProduct(any());
    }

    @Test
    @DisplayName("Should return 400 when creating product with invalid data")
    void shouldReturn400WhenCreatingProductWithInvalidData() throws Exception {
        ProductRequest invalidRequest = new ProductRequest("", "", new BigDecimal("-100"), "", -1);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(productService, never()).createProduct(any());
    }

    @Test
    @DisplayName("Should update product as admin")
    void shouldUpdateProductAsAdmin() throws Exception {
        when(productService.updateProduct(eq(productId), any())).thenReturn(productResponse);

        mockMvc.perform(put("/api/v1/products/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Product"));

        verify(productService).updateProduct(eq(productId), any());
    }

    @Test
    @DisplayName("Should return 403 when non-admin tries to update product")
    void shouldReturn403WhenNonAdminTriesToUpdateProduct() throws Exception {
        mockMvc.perform(put("/api/v1/products/{id}", productId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isForbidden());

        verify(productService, never()).updateProduct(any(), any());
    }

    @Test
    @DisplayName("Should delete product as admin")
    void shouldDeleteProductAsAdmin() throws Exception {
        doNothing().when(productService).deleteProduct(productId);

        mockMvc.perform(delete("/api/v1/products/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        verify(productService).deleteProduct(productId);
    }

    @Test
    @DisplayName("Should return 403 when non-admin tries to delete product")
    void shouldReturn403WhenNonAdminTriesToDeleteProduct() throws Exception {
        mockMvc.perform(delete("/api/v1/products/{id}", productId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        verify(productService, never()).deleteProduct(any());
    }
}