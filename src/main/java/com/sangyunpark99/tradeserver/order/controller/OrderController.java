package com.sangyunpark99.tradeserver.order.controller;

import com.sangyunpark99.tradeserver.order.controller.dto.PlaceOrderRequest;
import com.sangyunpark99.tradeserver.order.controller.dto.PlaceOrderResponse;
import com.sangyunpark99.tradeserver.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderController {

   private final OrderService orderService;

   @PostMapping("/api/orders")
   public ResponseEntity<PlaceOrderResponse> placeOrder(@Validated @RequestBody PlaceOrderRequest request) {
        Long orderId = orderService.placeOrder(request.accountId(), request.symbol(), request.quantity(), request.limitPrice());
        return ResponseEntity.status(HttpStatus.CREATED).body(new PlaceOrderResponse(orderId));
   }
}
