package com.sangyunpark99.tradeserver.order.repository;

import com.sangyunpark99.tradeserver.order.entity.Order;
import com.sangyunpark99.tradeserver.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findTop50ByOrderStatusOrderById(OrderStatus orderStatus);
}
