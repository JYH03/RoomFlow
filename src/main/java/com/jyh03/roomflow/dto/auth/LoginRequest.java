package com.jyh03.roomflow.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(max = 72) String password) {
}
