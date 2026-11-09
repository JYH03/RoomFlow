package com.jyh03.roomflow.dto.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
