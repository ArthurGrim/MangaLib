package de.mangalib.security;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.web.server.AbstractConfigurableWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/** Anmeldungsfreier lokaler Vertrauensbereich; LAN/Proxybetrieb benötigt ein eigenes Konzept. */
@Configuration(proxyBeanMethods = false)
public class LocalSecurityConfiguration {
    @Bean
    static BeanFactoryPostProcessor localOperatingModeGuard(Environment environment) {
        // Vor regulären Singletons abbrechen, damit unsichere Overrides keinen Listener öffnen.
        return factory -> validateLocalConfiguration(environment);
    }

    static void validateLocalConfiguration(Environment env) {
        require(env, "server.address", "127.0.0.1");
        require(env, "server.forward-headers-strategy", "none");
        require(env, "server.servlet.session.cookie.http-only", "true");
        require(env, "server.servlet.session.cookie.same-site", "strict");
        require(env, "server.servlet.session.tracking-modes", "cookie");
        require(env, "management.endpoints.web.exposure.include", "health");
        require(env, "management.endpoint.health.show-details", "never");
        require(env, "management.endpoint.health.show-components", "never");
        require(env, "management.endpoint.health.probes.enabled", "false");
        require(env, "management.endpoints.web.discovery.enabled", "false");
        if (env.getProperty("server.port", Integer.class, 8080) < 1
                || env.getProperty("server.port", Integer.class, 8080) > 65535
                || env.getProperty("server.ssl.enabled", Boolean.class, false)
                || env.containsProperty("server.ssl.bundle")
                || env.containsProperty("management.server.port")
                || env.containsProperty("server.servlet.session.cookie.domain")
                || env.getProperty("server.servlet.session.cookie.secure", Boolean.class, false)
                || !env.getProperty("server.servlet.session.cookie.path", "/").equals("/")
                || !env.getProperty("server.servlet.context-path", "").isEmpty()) {
            throw new IllegalStateException("Unzulässige Konfiguration für lokalen MangaLib-Betrieb.");
        }
    }

    @Bean
    static BeanPostProcessor localWebServerFactoryGuard(Environment env) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                // Auch spätere Factory-Customizer dürfen die Listener-Bindung nicht auf LAN erweitern.
                if (bean instanceof AbstractConfigurableWebServerFactory factory
                        && (factory.getAddress() == null || !"127.0.0.1".equals(factory.getAddress().getHostAddress())
                            || factory.getPort() != env.getProperty("server.port", Integer.class, 8080)
                            || (factory.getSsl() != null && factory.getSsl().isEnabled()))) {
                    throw new IllegalStateException("HTTP-Server muss den lokalen Bindungs-, Port- und HTTP-Vertrag einhalten.");
                }
                return bean;
            }
        };
    }

    private static void require(Environment env, String name, String expected) {
        if (!expected.equalsIgnoreCase(env.getProperty(name))) {
            throw new IllegalStateException("Unsicherer Override im lokalen Betrieb: " + name);
        }
    }

    @Bean
    SecurityFilterChain localSecurityFilterChain(HttpSecurity http, Environment env) throws Exception {
        return http
                .addFilterBefore(new LocalRequestFilter(env.getProperty("server.port", Integer.class, 8080)), CsrfFilter.class)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .build();
    }
}
