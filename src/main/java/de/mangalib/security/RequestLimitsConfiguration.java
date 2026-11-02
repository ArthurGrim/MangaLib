package de.mangalib.security;

import jakarta.servlet.DispatcherType;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Gemeinsam für regulären und manuellen Betrieb, unabhängig von Datenbankprofilen. */
@Configuration(proxyBeanMethods = false)
public class RequestLimitsConfiguration implements WebServerFactoryCustomizer<TomcatServletWebServerFactory>, Ordered {
    static final int MAX_FORM_POST_BYTES = 2 * 1024 * 1024;
    @Bean
    FilterRegistrationBean<RequestBodyLimitFilter> requestBodyLimits() {
        var registration = new FilterRegistrationBean<>(new RequestBodyLimitFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setAsyncSupported(false);
        registration.addUrlPatterns("/*");
        return registration;
    }

    // Nach Boots Property-Customizern anwenden. Kein Listener wird hier erzeugt.
    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }

    @Override public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers(connector -> {
            // Tomcats Formparserlimit ist kein JSON-Bodylimit; bisherigen 2-MiB-Rahmen erhalten.
            connector.setMaxPostSize(MAX_FORM_POST_BYTES);
            if (connector.getProtocolHandler() instanceof AbstractHttp11Protocol<?> protocol) {
                // Abgewiesene große Bodies nicht hinterher unbeschränkt für Keep-alive leeren.
                protocol.setMaxSwallowSize(0);
            }
        });
    }
}
