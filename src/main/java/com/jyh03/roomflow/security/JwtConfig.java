package com.jyh03.roomflow.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Configuration
public class JwtConfig {

    public static final String ISSUER = "roomflow";
    public static final String AUDIENCE = "roomflow-api";

    @Bean
    public SecretKey jwtSecretKey(@Value("${roomflow.jwt.secret:}") String encoded) {
        if (encoded.isBlank()) {
            throw new IllegalStateException("ROOMFLOW_JWT_SECRET 환경 변수를 설정하세요. docs/day12.md 참고.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT 키는 Base64 형식이어야 합니다.");
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT 키는 디코딩 후 최소 32바이트여야 합니다.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO),
                new JwtIssuerValidator(ISSUER),
                new JwtClaimValidator<Instant>("exp", expiration -> expiration != null),
                new JwtClaimValidator<List<String>>("aud", audience -> audience != null && audience.contains(AUDIENCE)),
                new JwtClaimValidator<String>("sub", JwtConfig::isValidUserId)));
        return decoder;
    }

    private static boolean isValidUserId(String subject) {
        try {
            return subject != null && Long.parseLong(subject) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
