package io.github.agentxzy.inboxpilot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/error").permitAll()
                .requestMatchers("/api/digest").authenticated()   // now requires real Google login
                .anyRequest().permitAll()
            )
            .oauth2Login(oauth2 -> {})   // enables "Login with Google" flow using your registered client
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}