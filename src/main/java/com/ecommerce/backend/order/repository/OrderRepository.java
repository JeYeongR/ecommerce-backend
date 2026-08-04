package com.ecommerce.backend.order.repository;

import com.ecommerce.backend.order.domain.Order;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByCustomerId(Long customerId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "orderItems")
    Optional<Order> findById(Long id);
}
