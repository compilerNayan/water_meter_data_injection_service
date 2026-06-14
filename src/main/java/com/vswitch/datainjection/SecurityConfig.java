package com.vswitch.datainjection;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@Profile("!test")
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/",
                                                "/actuator/health",
                                                "/actuator/info",
                                                "/api/test-data/**",
                                                "/ws/live",
                                                "/stream/**",
                                                "/devices/*/tenant")
                                        .permitAll()
                                .requestMatchers(
                                                "/users/**",
                                                "/tenants/**",
                                                "/v2/users/**",
                                                "/v2/tenants/**",
                                                "/api/**")
                                        .authenticated()
                                        .anyRequest()
                                        .permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        return http.build();
    }
}
