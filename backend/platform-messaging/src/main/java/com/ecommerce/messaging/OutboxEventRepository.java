package com.ecommerce.messaging;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.createdAt asc, e.id asc")
    List<OutboxEvent> findUnpublished(Pageable pageable);
}
