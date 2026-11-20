package com.sangyunpark99.tradeserver.order.repository;

import com.sangyunpark99.tradeserver.order.entity.Order;
import com.sangyunpark99.tradeserver.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findTop50ByOrderStatusOrderById(OrderStatus orderStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findWithLockById(Long orderId);
}
