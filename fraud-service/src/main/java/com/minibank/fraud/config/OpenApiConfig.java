package com.minibank.fraud.config;

import com.minibank.fraud.dto.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

@Configuration
public class OpenApiConfig {

    static final String BEARER = "Bearer Authentication";

    @Bean
    public OpenAPI customOpenAPI(ObjectProvider<BuildProperties> buildProperties) {
        // Version comes from pom.xml via spring-boot-maven-plugin's build-info goal
        String version = buildProperties.stream().map(BuildProperties::getVersion).findFirst().orElse("dev");
        return new OpenAPI()
                .info(new Info()
                        .title("MiniBank — Fraud & Audit Service API")
                        .version(version)
                        .description("Fraud detection and audit trail service (Elasticsearch-backed). All endpoints are ADMIN only."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components()
                        .addSecuritySchemes(BEARER,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste the accessToken from POST /api/v1/auth/login")));
    }

    /**
     * Documents the standard error responses on every operation, all using {@link ErrorResponse}:
     * 400 everywhere, 401/403 on secured operations, 404 where there is a path parameter, 500 everywhere.
     * Operations can still declare their own responses; those are never overwritten.
     */
    @Bean
    public OpenApiCustomizer standardErrorResponses() {
        return openApi -> {
            ModelConverters.getInstance().read(ErrorResponse.class).forEach(openApi.getComponents()::addSchemas);
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(op -> {
                boolean secured = op.getSecurity() == null || !op.getSecurity().isEmpty();
                boolean hasPathParam = op.getParameters() != null && op.getParameters().stream()
                        .anyMatch(p -> "path".equals(p.getIn()));

                addError(op, "400", "Invalid request: validation failed or a parameter is malformed");
                if (secured) {
                    addError(op, "401", "Missing, invalid or expired access token");
                    addError(op, "403", "Authenticated, but not allowed to access this resource");
                }
                if (hasPathParam) {
                    addError(op, "404", "Resource not found");
                }
                addError(op, "500", "Unexpected server error");
            }));
        };
    }

    private static void addError(Operation op, String code, String description) {
        ApiResponses responses = Objects.requireNonNullElseGet(op.getResponses(), ApiResponses::new);
        op.setResponses(responses);
        if (responses.containsKey(code)) {
            return;
        }
        Schema<?> schema = new Schema<>().$ref("#/components/schemas/ErrorResponse");
        responses.addApiResponse(code, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", new MediaType().schema(schema))));
    }
}
