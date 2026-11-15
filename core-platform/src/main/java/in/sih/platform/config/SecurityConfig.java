package in.sih.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return http
            .cors(cors -> {})
            .exceptionHandling(errors -> errors.authenticationEntryPoint((req,res,ex)->ApiErrors.write(req,res,401)).accessDeniedHandler((req,res,ex)->ApiErrors.write(req,res,403)))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health").permitAll()
                .requestMatchers("/", "/login", "/explore", "/challenges/*", "/universities/*/departments", "/pages/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/otp/request", "/api/auth/otp/verify").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/challenges", "/api/challenges/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/organizations/*/departments").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated())
            .oauth2ResourceServer(resource -> resource.authenticationEntryPoint((req,res,ex)->ApiErrors.write(req,res,401)).accessDeniedHandler((req,res,ex)->ApiErrors.write(req,res,403)).jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
            .build();
    }
}
