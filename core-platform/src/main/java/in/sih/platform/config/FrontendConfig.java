package in.sih.platform.config;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.*;
@Configuration
public class FrontendConfig {
    @Bean
    io.swagger.v3.oas.models.OpenAPI apiDocumentation() {
        return new io.swagger.v3.oas.models.OpenAPI().components(new io.swagger.v3.oas.models.Components().addSecuritySchemes("bearerAuth",new io.swagger.v3.oas.models.security.SecurityScheme().type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
    @Bean
    org.springframework.web.filter.CorsFilter corsFilter(@org.springframework.beans.factory.annotation.Qualifier("corsConfigurationSource") CorsConfigurationSource source) {
        var filter=new org.springframework.web.filter.CorsFilter(source);
        filter.setCorsProcessor(new DefaultCorsProcessor() {
            @Override protected void rejectRequest(org.springframework.http.server.ServerHttpResponse response) throws java.io.IOException { response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN); }
            @Override public boolean processRequest(CorsConfiguration config, jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
                boolean accepted=super.processRequest(config,request,response);
                if(!accepted && !response.isCommitted()) { response.resetBuffer(); ApiErrors.write(request,response,403); }
                return accepted;
            }
        });
        return filter;
    }
    @Bean
    CorsConfigurationSource corsConfigurationSource(Environment env) {
        boolean prod=java.util.Arrays.asList(env.getActiveProfiles()).contains("prod");
        String origins=env.getProperty("cors.allowed-origins",env.getProperty("CORS_ALLOWED_ORIGINS",prod?"":"http://localhost:5173,http://localhost:3000"));
        CorsConfiguration config=new CorsConfiguration();config.setAllowedOrigins(java.util.Arrays.stream(origins.split(",")).map(String::trim).filter(s->!s.isEmpty()).toList());
        config.setAllowedHeaders(java.util.List.of("Authorization","Content-Type"));config.setAllowedMethods(java.util.List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",config);return source;
    }
}
