package com.ecommerce.backend.order.repository;

import com.ecommerce.backend.order.domain.Order;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByCustomerId(Long customerId, Pageable pageable);

    /** PENDING인 주문만 PAYING으로 선점한다. 1이면 선점 성공, 0이면 이미 다른 곳에서 처리 중이거나 처리됨. */
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = com.ecommerce.backend.order.domain.OrderStatus.PAYING "
        + "where o.id = :id and o.status = com.ecommerce.backend.order.domain.OrderStatus.PENDING")
    int claimForPayment(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = "orderItems")
    Optional<Order> findById(Long id);
}
