package com.sangyunpark99.tradeserver.fill.service;

import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.account.repository.AccountRepository;
import com.sangyunpark99.tradeserver.fill.Fill;
import com.sangyunpark99.tradeserver.fill.controller.dto.MyFillResponse;
import com.sangyunpark99.tradeserver.fill.repository.FillRepository;
import com.sangyunpark99.tradeserver.order.repository.OrderRepository;
import com.sangyunpark99.tradeserver.order.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FillServiceTest {

    @Autowired
    private FillService fillService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private FillRepository fillRepository;

    @AfterEach
    void tearDown() {
        fillRepository.deleteAll();
        orderRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private Long openAccount(long userId) {
        return accountRepository.save(new Account(userId, new BigDecimal("100000000"))).getId();
    }

    private Long placeOrder(long userId, Long accountId, String symbol) {
        return orderService.placeOrder(userId, accountId, symbol, 100, new BigDecimal("100"));
    }

    private String saveFill(Long orderId) {
        String fillId = UUID.randomUUID().toString();
        fillRepository.save(new Fill(orderId, new BigDecimal("99"), 1, fillId));
        return fillId;
    }

    @Test
    @DisplayName("내 계좌 주문의 체결만 나오고 다른 사용자의 체결은 나오지 않는다")
    void returnsOnlyMyFills() {
        Long myOrder = placeOrder(1L, openAccount(1L), "AAPL");
        Long otherOrder = placeOrder(2L, openAccount(2L), "TSLA");
        String mine = saveFill(myOrder);
        saveFill(otherOrder);

        List<MyFillResponse> fills = fillService.findMyFills(1L);

        assertThat(fills).extracting(MyFillResponse::fillId).containsExactly(mine);
    }

    @Test
    @DisplayName("여러 주문의 체결이 최근에 반영된 순서로 나오고 주문의 종목이 붙는다")
    void returnsLatestFirstWithSymbol() {
        Long accountId = openAccount(1L);
        Long aapl = placeOrder(1L, accountId, "AAPL");
        Long tsla = placeOrder(1L, accountId, "TSLA");
        String first = saveFill(aapl);
        String second = saveFill(tsla);
        String third = saveFill(aapl);

        List<MyFillResponse> fills = fillService.findMyFills(1L);

        assertThat(fills).extracting(MyFillResponse::fillId).containsExactly(third, second, first);
        assertThat(fills).extracting(MyFillResponse::symbol).containsExactly("AAPL", "TSLA", "AAPL");
        assertThat(fills.get(1)).satisfies(fill -> {
            assertThat(fill.orderId()).isEqualTo(tsla);
            assertThat(fill.quantity()).isEqualTo(1);
            assertThat(fill.price()).isEqualByComparingTo("99");
            assertThat(fill.filledAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("체결이 30건을 넘으면 최근 30건만 나온다")
    void returnsAtMostThirty() {
        Long orderId = placeOrder(1L, openAccount(1L), "AAPL");
        List<String> fillIds = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            fillIds.add(saveFill(orderId));
        }

        List<MyFillResponse> fills = fillService.findMyFills(1L);

        assertThat(fills).hasSize(30);
        assertThat(fills.get(0).fillId()).isEqualTo(fillIds.get(34));
        assertThat(fills.get(29).fillId()).isEqualTo(fillIds.get(5));
    }

    @Test
    @DisplayName("계좌가 없는 사용자는 빈 목록을 받는다")
    void returnsEmptyWithoutAccount() {
        assertThat(fillService.findMyFills(99L)).isEmpty();
    }

    @Test
    @DisplayName("계좌는 있지만 체결이 없으면 빈 목록을 받는다")
    void returnsEmptyWithoutFills() {
        placeOrder(1L, openAccount(1L), "AAPL");

        assertThat(fillService.findMyFills(1L)).isEmpty();
    }
}
