package com.sangyunpark99.tradeserver.auth.service;

import com.sangyunpark99.tradeserver.auth.jwt.JwtProvider;
import com.sangyunpark99.tradeserver.auth.controller.dto.LoginResponse;
import com.sangyunpark99.tradeserver.common.exception.ErrorCode;
import com.sangyunpark99.tradeserver.common.exception.TradeException;
import com.sangyunpark99.tradeserver.user.User;
import com.sangyunpark99.tradeserver.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public long signUp(String email, String name, String password) {

        if(userRepository.existsByEmail(email)) {
            throw new TradeException(ErrorCode.DUPLICATE_EMAIL);
        }

        String passwordHash = passwordEncoder.encode(password);
        User user = new User(email, name, passwordHash);

        User savedUser;

        try {
            savedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new TradeException(ErrorCode.DUPLICATE_EMAIL);
        }

        return savedUser.getId();
    }

    public LoginResponse login(String email, String password) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new TradeException(ErrorCode.LOGIN_FAILED));

        boolean check = passwordEncoder.matches(password, user.getPasswordHash());

        if(!check) {
            throw new TradeException(ErrorCode.LOGIN_FAILED);
        }

        String accessToken = jwtProvider.createAccessToken(user.getId());
        long expiresIn = jwtProvider.getAccessTokenExpiration().toSeconds();

        return new LoginResponse(accessToken, expiresIn);
     }
}
