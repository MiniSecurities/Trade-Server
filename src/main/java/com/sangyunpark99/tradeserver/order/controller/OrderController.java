package com.sangyunpark99.tradeserver.order.controller;

import com.sangyunpark99.tradeserver.auth.UserContext;
import com.sangyunpark99.tradeserver.order.controller.dto.FillRequest;
import com.sangyunpark99.tradeserver.order.controller.dto.OrderDetailResponse;
import com.sangyunpark99.tradeserver.order.controller.dto.PlaceOrderRequest;
import com.sangyunpark99.tradeserver.order.controller.dto.PlaceOrderResponse;
import com.sangyunpark99.tradeserver.order.facade.OrderFacade;
import com.sangyunpark99.tradeserver.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderController {

   private final OrderFacade orderFacade;
   private final OrderService orderService;

   @PostMapping("/api/orders")
   public ResponseEntity<PlaceOrderResponse> placeOrder(@Validated @RequestBody PlaceOrderRequest request) {
       long userId = UserContext.get();
       Long orderId = orderFacade.placeOrder(userId, request.accountId(), request.symbol(), request.quantity(), request.limitPrice());
       return ResponseEntity.status(HttpStatus.CREATED).body(new PlaceOrderResponse(orderId));
   }

   @PostMapping("/api/fills")
   public ResponseEntity<Void> applyFill(@Validated @RequestBody FillRequest request) {
       orderFacade.applyFill(request.fillId(), request.orderId(), request.fillQuantity(), request.fillPrice());
       return ResponseEntity.noContent().build();
   }

   @GetMapping("/api/orders/{orderId}")
   public ResponseEntity<OrderDetailResponse> getOrder(@PathVariable long orderId) {
       return ResponseEntity.ok(orderService.getOrder(orderId));
   }
}
