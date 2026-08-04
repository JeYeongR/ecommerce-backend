package com.ecommerce.backend.product.repository;

import com.ecommerce.backend.product.domain.Product;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Override
    @EntityGraph(attributePaths = "options")
    Optional<Product> findById(Long id);
}
