package com.neelastack.lakhdatar.config;

import com.neelastack.lakhdatar.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean; import org.springframework.context.annotation.Configuration; import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity; import org.springframework.security.config.annotation.web.builders.HttpSecurity; import org.springframework.security.config.http.SessionCreationPolicy; import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.security.web.AuthenticationEntryPoint; import org.springframework.security.web.SecurityFilterChain; import org.springframework.security.web.access.AccessDeniedHandler; import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter; import org.springframework.web.cors.CorsConfiguration; import org.springframework.web.cors.CorsConfigurationSource; import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays; import java.util.List;

@Configuration @EnableMethodSecurity @RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter; private final AppProperties props;
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
    @Bean SecurityFilterChain filterChain(HttpSecurity http)throws Exception{
        AuthenticationEntryPoint ep=(req,res,ex)->{res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");};
        AccessDeniedHandler dp=(req,res,ex)->{res.setStatus(403);res.setContentType("application/json");res.getWriter().write("{\"status\":403,\"code\":\"FORBIDDEN\",\"message\":\"Access denied\"}");};
        http.csrf(c->c.disable()).cors(c->c.configurationSource(cors())).sessionManagement(sm->sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).exceptionHandling(e->e.authenticationEntryPoint(ep).accessDeniedHandler(dp))
            .headers(h->h.contentTypeOptions(c->{}).frameOptions(f->f.deny()))
            .authorizeHttpRequests(a->a
                .requestMatchers("/actuator/health","/actuator/health/**","/actuator/info","/actuator/prometheus").permitAll()
                .requestMatchers("/api/v1/auth/**","/api/v1/public/**","/api/v1/webhooks/**","/api/v1/setup/**","/robots.txt","/sitemap.xml").permitAll()
                .requestMatchers("/api/v1/checkin/**","/api/v1/staff/**").hasAnyRole("STAFF","EVENT_MANAGER","ORGANIZER","ADMIN")
                .requestMatchers("/api/v1/admin/payments/**").hasAnyRole("ADMIN","FINANCE","ORGANIZER")
                .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN","ORGANIZER","EVENT_MANAGER","FINANCE")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    private CorsConfigurationSource cors(){
        CorsConfiguration c=new CorsConfiguration(); c.setAllowedOrigins(Arrays.stream(props.cors().allowedOrigins().split(",")).map(String::trim).filter(s->!s.isBlank()).toList());
        c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS")); c.setAllowedHeaders(List.of("Authorization","Content-Type","X-Correlation-ID","X-Ticket-Token","X-Initial-Setup-Token")); c.setExposedHeaders(List.of("X-Correlation-ID")); c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource s=new UrlBasedCorsConfigurationSource(); s.registerCorsConfiguration("/api/**",c); return s;
    }
}
