package com.cresensolutions.userservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI userServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("User Service API")
                        .description("API documentation for authentication and user management endpoints.")
                        .version("v1")
                        .contact(new Contact()
                                .name("Cresen Solutions")
                                .email("support@cresensolutions.com")));
    }
}
