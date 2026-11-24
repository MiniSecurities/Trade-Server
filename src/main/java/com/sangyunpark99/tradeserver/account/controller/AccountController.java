package com.sangyunpark99.tradeserver.account.controller;

import com.sangyunpark99.tradeserver.account.controller.dto.DepositAccountResponse;
import com.sangyunpark99.tradeserver.account.facade.AccountFacade;
import com.sangyunpark99.tradeserver.account.controller.dto.DepositAccountRequest;
import com.sangyunpark99.tradeserver.account.controller.dto.OpenAccountResponse;
import com.sangyunpark99.tradeserver.account.service.AccountService;
import com.sangyunpark99.tradeserver.auth.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AccountFacade accountFacade;

    @PostMapping
    public ResponseEntity<OpenAccountResponse> open() {
        long userId = UserContext.get();
        Long accountId = accountService.open(userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(new OpenAccountResponse(accountId));
    }

    @PostMapping("/deposit")
    public ResponseEntity<DepositAccountResponse> deposit(@Valid @RequestBody DepositAccountRequest request) {
        long userId = UserContext.get();
        DepositAccountResponse response = accountFacade.deposit(userId, request.amount());

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
