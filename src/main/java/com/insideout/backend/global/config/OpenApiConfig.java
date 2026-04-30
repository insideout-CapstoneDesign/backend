package com.insideout.backend.global.config;


import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {

        String jwtSchemeName = "JWT";

        Components components =
                new Components()
                        .addSecuritySchemes(
                                jwtSchemeName,
                                new SecurityScheme()
                                        .name(jwtSchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT"));

        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList(jwtSchemeName))
                .info(
                        new Info()
                                .title("INSIDEOUT API")
                                .description("INSIDEOUT backend API documentation")
                                .version("v1.1")
                                .contact(
                                        new Contact().name("INSIDEOUT Team").email("dev@insideout.local"))
                                .license(new License().name("Proprietary")))
                .components(components);
    }
}
