package com.athenet.competition.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.cors.admin-origins:http://localhost:4200}")
    private List<String> adminOrigins;

    @Value("${app.cors.public-origins:http://localhost:5173}")
    private List<String> publicOrigins;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("DIRECTOR")
                .requestMatchers("/api/delegate/**").hasRole("DELEGATE")
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter defaultAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> defaultAuthorities = defaultAuthoritiesConverter.convert(jwt);
            Collection<GrantedAuthority> authorities = defaultAuthorities != null
                ? new ArrayList<>(defaultAuthorities)
                : new ArrayList<>();

            // Extrae el claim "roles" que emite Microsoft Entra ID y lo mapea a authorities de
            // Spring (mismo criterio que api-athenet-events, para que un token de director
            // sirva igual en los dos microservicios).

            /* 
                Extrae el claim "roles" que emite Microsoft Entra ID y lo mapea a authorities de
                Spring (mismo criterio que api-events, para que un token de director sirva
                igual en los dos microservicios).
            */
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                for (String role : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // Admin desde Angular contra /api/admin/**: manda token, asi que va con credenciales.
        CorsConfiguration adminCfg = new CorsConfiguration();
        adminCfg.setAllowedOrigins(adminOrigins);
        adminCfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        adminCfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        adminCfg.setExposedHeaders(List.of("Authorization", "Link", "X-Total-Count"));
        adminCfg.setAllowCredentials(true);
        adminCfg.setMaxAge(3600L);

        source.registerCorsConfiguration("/api/admin/**", adminCfg);

        // El sitio publico lee /api/public/** desde el navegador: anonimo, no credenciales.
        CorsConfiguration publicCfg = new CorsConfiguration();
        publicCfg.setAllowedOrigins(publicOrigins);
        publicCfg.setAllowedMethods(List.of("GET", "OPTIONS"));
        publicCfg.setAllowedHeaders(List.of("Content-Type", "Accept", "Origin"));
        publicCfg.setMaxAge(3600L);

        source.registerCorsConfiguration("/api/public/**", publicCfg);

        return source;
    }
}
