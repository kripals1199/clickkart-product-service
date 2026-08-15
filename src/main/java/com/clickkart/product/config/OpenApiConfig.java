// src/main/java/com/clickkart/category/config/OpenApiConfig.java
package com.clickkart.product.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    /**
     * The bearer scheme is declared but deliberately NOT applied as a global security requirement,
     * unlike User Service where every endpoint needs a token. Most of this API is public catalog
     * browsing; marking it all as secured would tell an integrator they need credentials to read the
     * shop front. The admin operations carry the requirement individually via their own annotations.
     */
    @Bean
    public OpenAPI productServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ClickKart Product Service")
                        .version("1.0.0")
                        .description("Seller listings with variants and a moderation workflow. Browsing is public; selling requires a verified seller; approving requires ADMIN."))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
