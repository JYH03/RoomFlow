package com.jyh03.roomflow.service;

import com.jyh03.roomflow.domain.user.User;
import com.jyh03.roomflow.dto.auth.*;
import com.jyh03.roomflow.global.error.DuplicateEmailException;
import com.jyh03.roomflow.repository.UserRepository;
import com.jyh03.roomflow.security.JwtConfig;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {
    private static final long TOKEN_SECONDS = 3600;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.dummyPasswordHash = passwordEncoder.encode("unused-password-for-timing");
    }

    @Transactional
    public UserResponse signup(SignupRequest request) {
        validatePasswordBytes(request.password());
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        User user = new User(email, passwordEncoder.encode(request.password()), request.name().strip());
        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            // Also handles two concurrent signups passing the existence check.
            throw new DuplicateEmailException();
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        validatePasswordBytes(request.password());
        User user = userRepository.findByEmail(normalizeEmail(request.email())).orElse(null);
        String hash = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !matches) {
            throw new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER).audience(List.of(JwtConfig.AUDIENCE))
                .subject(user.getId().toString())
                .issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(TOKEN_SECONDS)).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", TOKEN_SECONDS, UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        return userRepository.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> new BadCredentialsException("유효하지 않은 사용자입니다."));
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private void validatePasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        }
    }
}
