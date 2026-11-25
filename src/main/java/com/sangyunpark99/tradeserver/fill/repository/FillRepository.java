package com.sangyunpark99.tradeserver.fill.repository;

import com.sangyunpark99.tradeserver.fill.Fill;
import com.sangyunpark99.tradeserver.fill.controller.dto.MyFillResponse;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FillRepository extends JpaRepository<Fill, Long> {

    boolean existsByFillId(String fillId);

    List<Fill> findByOrderIdOrderByIdAsc(Long orderId);

    @Query(value = """
            select new com.sangyunpark99.tradeserver.fill.controller.dto.MyFillResponse(
                f.fillId, f.orderId, o.symbol, f.quantity, f.price, f.createdAt)
           from Fill f
           join Order o on f.orderId = o.id
           join Account a on o.accountId = a.id
           where a.userId = :userId
           order by f.id desc
        """)
    List<MyFillResponse> findMyFills(long userId, Limit limit);
}
