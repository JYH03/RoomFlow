package com.jyh03.roomflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI roomFlowApi() {
        return new OpenAPI().info(new Info().title("RoomFlow API").version("1.0.0")
                .description("13개 미팅룸 예약·대기열·승급·체크인 API. 로그인 토큰을 Authorize에 입력하세요."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
