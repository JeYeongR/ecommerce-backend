package com.ecommerce.backend.order.event;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface OutboxEventRepository extends Repository<OutboxEvent, Long> {

    OutboxEvent save(OutboxEvent event);

    /** 다른 인스턴스가 잡고 있는 행은 건너뛴다. 호출자 트랜잭션이 끝날 때까지 행 락이 유지된다. */
    @Query(value = "select * from outbox_event where status = 'PENDING' order by id limit :limit for update skip locked",
        nativeQuery = true)
    List<OutboxEvent> lockPending(@Param("limit") int limit);
}
