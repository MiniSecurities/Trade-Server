package com.sangyunpark99.tradeserver.user.controller;

import com.sangyunpark99.tradeserver.user.controller.dto.SignUpRequest;
import com.sangyunpark99.tradeserver.user.controller.dto.SignUpResponse;
import com.sangyunpark99.tradeserver.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        Long userId = userService.signUp(request.email(), request.name(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(new SignUpResponse(userId));
    }
}
