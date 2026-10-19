package in.codebasket.cbwa.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("CB WhatsApp Gateway API").version("1.0")
                        .description("Dashboard API (JWT) and Partner Public API (X-API-KEY / X-API-SECRET).")
                        .contact(new Contact().name("CodeBasket IT Services & Co")))
                .components(new Components()
                        .addSecuritySchemes("bearer", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes("apiKey", new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-API-KEY"))
                        .addSecuritySchemes("apiSecret", new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-API-SECRET")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder().group("partner-public-api").pathsToMatch("/api/v1/public/**")
                .addOpenApiCustomizer(api -> api.setSecurity(java.util.List.of(new SecurityRequirement().addList("apiKey").addList("apiSecret"))))
                .build();
    }

    @Bean
    public GroupedOpenApi dashboardApi() {
        return GroupedOpenApi.builder().group("dashboard-api").pathsToMatch("/api/**").pathsToExclude("/api/v1/public/**").build();
    }
}
