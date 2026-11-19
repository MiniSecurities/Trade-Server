package com.sangyunpark99.tradeserver.order.repository;

import com.sangyunpark99.tradeserver.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
