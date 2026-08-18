package com.ecommerce.backend.product.repository;

import com.ecommerce.backend.product.domain.ProductOption;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select po from ProductOption po where po.id in :ids order by po.id")
    List<ProductOption> findAllByIdInForUpdate(@Param("ids") List<Long> ids);
}
