package com.pm.apigateway.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class JwtValidationGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {
    private final WebClient webClient;

    public JwtValidationGatewayFilterFactory(
            WebClient.Builder webClientBuilder,
            @Value("${auth.service.url}") String authServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(authServiceUrl).build();
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            String token = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (token == null || !token.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            return webClient.get()
                    .uri("/validate")
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .retrieve()
                    .bodyToMono(RoleResponse.class)
                    .flatMap(roleResponse -> {
                        if (roleResponse == null
                                || roleResponse.getRole() == null
                                || roleResponse.getRole().isEmpty()
                                || roleResponse.getStatus() != 200) {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }

                        var requestBuilder = exchange.getRequest().mutate()
                                .header("X-Role", roleResponse.getRole())
                                .header("X-User-Role", roleResponse.getRole());

                        if (roleResponse.getEmail() != null && !roleResponse.getEmail().isBlank()) {
                            requestBuilder.header("X-User-Email", roleResponse.getEmail());
                        }

                        if (roleResponse.getUserId() != null && !roleResponse.getUserId().isBlank()) {
                            requestBuilder.header("X-User-Id", roleResponse.getUserId());
                        }

                        System.out.println("userId = " + roleResponse.getUserId());
                        System.out.println("email = " + roleResponse.getEmail());
                        System.out.println("role = " + roleResponse.getRole());
                        System.out.println("status = " + roleResponse.getStatus());

                        var mutatedExchange = exchange.mutate()
                                .request(requestBuilder.build())
                                .build();

                        return chain.filter(mutatedExchange);
                    })
                    .onErrorResume(error -> {
                        error.printStackTrace();
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    });
        };
    }
}
