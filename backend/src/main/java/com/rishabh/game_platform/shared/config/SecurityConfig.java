package com.rishabh.game_platform.shared.config;

import com.rishabh.game_platform.shared.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. Ensure CORS is enabled and hooked to a configuration source
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 2. CSRF must be disabled for SockJS to negotiate the connection
            .csrf(csrf -> csrf.disable())

            // 3. Configure exception handling: 401 for unauthenticated, 403 for access denied
            .exceptionHandling(exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, authException) ->
                            response.sendError(
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "Unauthorized"
                            )
                    )
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) ->
                            response.sendError(
                                HttpServletResponse.SC_FORBIDDEN,
                                "Forbidden"
                            )
                    )
            )

            // 4. Permit WebSocket handshake, authentication endpoints, error path, and health
            .authorizeHttpRequests(auth ->
                auth
                    .requestMatchers(
                        "/ws/**",
                        "/auth/api/**",
                        "/error",
                        "/health"
                    )
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            )

            // 5. Stateless session policy for JWT authentication
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 6. Configure authentication provider & JWT filter
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(
                jwtAuthFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    // CorsConfigurationSource bean to allow localhost:5173
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow local development and the deployed frontend origin. Use
        // allowed origin patterns to support strict matching or wildcards.
        configuration.setAllowedOriginPatterns(
            Arrays.asList(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "https://game-platform-orpin-zeta.vercel.app"
            )
        );
        configuration.setAllowedMethods(
            Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS")
        );
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();
        // Applies CORS rules to all routes
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
