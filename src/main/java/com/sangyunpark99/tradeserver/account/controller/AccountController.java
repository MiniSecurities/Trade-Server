package com.sangyunpark99.tradeserver.account.controller;

import com.sangyunpark99.tradeserver.account.controller.dto.OpenAccountResponse;
import com.sangyunpark99.tradeserver.account.service.AccountService;
import com.sangyunpark99.tradeserver.auth.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<OpenAccountResponse> open() {
        long userId = UserContext.get();
        Long accountId = accountService.open(userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(new OpenAccountResponse(accountId));
    }
}
