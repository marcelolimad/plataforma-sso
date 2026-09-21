package br.dirap.sso;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean UserDetailsService users(JdbcTemplate db) {
        return email -> db.query("SELECT email, senha_hash, perfil FROM usuarios WHERE lower(email)=lower(?) AND ativo=true",
                (rs, n) -> new User(rs.getString("email"), rs.getString("senha_hash"),
                        List.of(new SimpleGrantedAuthority("ROLE_" + rs.getString("perfil")))), email)
                .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }

    @Bean SecurityFilterChain filterChain(HttpSecurity http, JdbcTemplate db) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/login").permitAll()
                .requestMatchers("/usuarios/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/dashboard", true).permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
            .addFilterAfter(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                        FilterChain chain) throws ServletException, IOException {
                    var auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                        Integer ativos = db.queryForObject("SELECT count(*) FROM usuarios WHERE lower(email)=lower(?) AND ativo=true",
                                Integer.class, auth.getName());
                        if (ativos == null || ativos == 0) {
                            SecurityContextHolder.clearContext();
                            if (request.getSession(false) != null) request.getSession(false).invalidate();
                            response.sendRedirect(request.getContextPath() + "/login?disabled");
                            return;
                        }
                    }
                    chain.doFilter(request,response);
                }
            }, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean org.springframework.boot.CommandLineRunner bootstrapAdmin(JdbcTemplate db, PasswordEncoder encoder,
            @Value("${SSO_ADMIN_EMAIL:}") String email, @Value("${SSO_ADMIN_PASSWORD:}") String senha) {
        return args -> {
            Integer count = db.queryForObject("SELECT COUNT(*) FROM usuarios", Integer.class);
            if (count != null && count == 0) {
                if (email.isBlank() || senha.length() < 12) throw new IllegalStateException(
                    "Defina SSO_ADMIN_EMAIL e SSO_ADMIN_PASSWORD (mínimo 12 caracteres) para criar o primeiro usuário.");
                db.update("INSERT INTO usuarios(nome,email,senha_hash,perfil) VALUES (?,?,?,?)",
                        "Administrador", email.trim(), encoder.encode(senha), "ADMIN");
            }
        };
    }
}
