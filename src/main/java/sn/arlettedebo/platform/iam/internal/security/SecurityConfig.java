package sn.arlettedebo.platform.iam.internal.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import java.util.Map;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * API sans état : authentification par JWT Bearer, aucun cookie de session, CSRF inutile
 * (le seul cookie — refresh — est HttpOnly, SameSite=Strict et limité à /api/v1/auth).
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

    private static final String[] PUBLIC = {
        "/api/v1/auth/connexion",
        "/api/v1/auth/rafraichir",
        "/api/v1/auth/deconnexion",
        "/api/v1/public/**",
        "/actuator/health/**",
        "/actuator/info",
    };

    /** Documentation et point de jeton Swagger : publics uniquement si la documentation est exposée (dev). */
    private static final String[] API_DOCS = {"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/api/v1/auth/swagger/jeton"};

    @Bean
    SecurityFilterChain api(HttpSecurity http, SecurityProperties props, PlatformJwtAuthenticationConverter converter,
            ProblemSecurityHandlers handlers) {
        http
            // API sans état authentifiée par en-tête Bearer : pas de jeton CSRF (non exploitable sans cookie).
            // Les deux routes authentifiées par cookie sont protégées par CookieCsrfGuard + SameSite=Strict.
            .csrf(AbstractHttpConfigurer::disable) // NOSONAR java:S4502 — justifié ci-dessus
            .addFilterBefore(new CookieCsrfGuard(props.refreshCookie().name(), handlers), BearerTokenAuthenticationFilter.class)
            .addFilterAfter(new ChangementMotDePasseGuard(handlers), BearerTokenAuthenticationFilter.class)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives(props.exposeApiDocs()
                        ? "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'"
                        : "default-src 'none'; frame-ancestors 'none'"))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .frameOptions(f -> f.deny())
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                auth.requestMatchers(PUBLIC).permitAll();
                if (props.exposeApiDocs()) {
                    auth.requestMatchers(API_DOCS).permitAll();
                }
                auth.requestMatchers("/actuator/**").hasAnyAuthority("PROFIL_DG", "PROFIL_MANAGER");
                auth.anyRequest().authenticated();
            })
            .oauth2ResourceServer(rs -> rs
                .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                .authenticationEntryPoint(handlers.entryPoint())
                .accessDeniedHandler(handlers.accessDeniedHandler()))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(handlers.entryPoint())
                .accessDeniedHandler(handlers.accessDeniedHandler()));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties props) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(props.corsAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "X-Client"));
        cors.setExposedHeaders(List.of("Content-Disposition"));
        cors.setAllowCredentials(true); // cookie de rafraîchissement
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    /** BCrypt (coût 12) par défaut ; préfixe {id} pour migrer d'algorithme sans casser les comptes. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(12)));
    }
}
