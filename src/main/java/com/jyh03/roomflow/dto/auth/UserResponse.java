package com.jyh03.roomflow.dto.auth;

import com.jyh03.roomflow.domain.user.User;

public record UserResponse(Long id, String email, String name) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName());
    }
}
