package com.sangyunpark99.tradeserver.order.controller;

import com.sangyunpark99.tradeserver.order.controller.dto.FillRequest;
import com.sangyunpark99.tradeserver.order.controller.dto.PlaceOrderRequest;
import com.sangyunpark99.tradeserver.order.controller.dto.PlaceOrderResponse;
import com.sangyunpark99.tradeserver.order.facade.OrderFacade;
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

   private final OrderFacade orderFacade;

   @PostMapping("/api/orders")
   public ResponseEntity<PlaceOrderResponse> placeOrder(@Validated @RequestBody PlaceOrderRequest request) {
        Long orderId = orderFacade.placeOrder(request.accountId(), request.symbol(), request.quantity(), request.limitPrice());
        return ResponseEntity.status(HttpStatus.CREATED).body(new PlaceOrderResponse(orderId));
   }

   @PostMapping("/api/fills")
   public ResponseEntity<Void> applyFill(@Validated @RequestBody FillRequest request) {
        orderFacade.applyFill(request.fillId(), request.orderId(), request.fillQuantity(), request.fillPrice());
        return ResponseEntity.noContent().build();
   }
}
