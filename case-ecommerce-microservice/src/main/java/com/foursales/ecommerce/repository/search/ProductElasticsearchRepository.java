package com.foursales.ecommerce.repository.search;

import com.foursales.ecommerce.entity.Product;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository("productSearchRepository")
public interface ProductElasticsearchRepository extends ElasticsearchRepository<Product, UUID> {

       @Query("{\"bool\": {" +
                     "\"must\": [" +
                     "  {\"match\": {\"name\": {\"query\": \"?0\", \"fuzziness\": \"AUTO\"}}}," +
                     "  {\"range\": {\"stockQuantity\": {\"gt\": 0}}}" +
                     "]" +
                     "}}")
       List<Product> findByNameWithFuzziness(String name);

       @Query("{\"range\": {\"stockQuantity\": {\"gt\": 0}}}")
       List<Product> findAllInStock();
}