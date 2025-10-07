package com.foursales.eventconsumer.repository.search;

import com.foursales.eventconsumer.entity.Product;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository("productSearchRepository")
public interface ProductElasticsearchRepository extends ElasticsearchRepository<Product, UUID> {
}