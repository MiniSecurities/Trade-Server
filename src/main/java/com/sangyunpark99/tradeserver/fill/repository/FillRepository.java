package com.sangyunpark99.tradeserver.fill.repository;

import com.sangyunpark99.tradeserver.fill.Fill;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FillRepository extends JpaRepository<Fill, Long> {

    boolean existsByFillId(String fillId);
}
