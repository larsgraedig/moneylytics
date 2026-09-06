package com.moneylytics.api.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.reactive.config.ResourceHandlerRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router
import java.net.URI

@Profile("local")
@Configuration
class SwaggerUiConfiguration : WebFluxConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry
            .addResourceHandler("/swagger-ui/**")
            .addResourceLocations(
                "classpath:/swagger-ui/",
                "classpath:/META-INF/resources/webjars/swagger-ui/5.20.1/",
            )
    }

    @Bean
    fun swaggerUiRedirectRouter(): RouterFunction<ServerResponse> =
        router {
            GET("/swagger-ui.html") {
                ServerResponse.temporaryRedirect(URI.create("/swagger-ui/index.html")).build()
            }
            GET("/") {
                ServerResponse.temporaryRedirect(URI.create("/swagger-ui.html")).build()
            }
        }

    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Moneylytics API")
                    .description("Import and analyse personal finance transactions")
                    .version("1.0.0"),
            )
}
