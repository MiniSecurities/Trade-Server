package com.sangyunpark99.tradeserver.fill.controller;

import com.sangyunpark99.tradeserver.auth.UserContext;
import com.sangyunpark99.tradeserver.fill.controller.dto.MyFillResponse;
import com.sangyunpark99.tradeserver.fill.service.FillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/fills")
@RequiredArgsConstructor
public class FillController {

    private final FillService fillService;

    @GetMapping("/me")
    public ResponseEntity<List<MyFillResponse>> findMyFills() {
        long userId = UserContext.get();
        return ResponseEntity.ok(fillService.findMyFills(userId));
    }
}
