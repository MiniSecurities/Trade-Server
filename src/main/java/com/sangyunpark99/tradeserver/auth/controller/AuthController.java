package com.sangyunpark99.tradeserver.auth.controller;

import com.sangyunpark99.tradeserver.auth.controller.dto.SignUpRequest;
import com.sangyunpark99.tradeserver.auth.controller.dto.SignUpResponse;
import com.sangyunpark99.tradeserver.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        Long userId = authService.signUp(request.email(), request.name(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(new SignUpResponse(userId));
    }
}
