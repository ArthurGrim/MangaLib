package de.mangalibmanual;

import java.util.Arrays;
import javax.sql.DataSource;
import de.mangalib.service.ScraperGateway;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;

/** Eigenständiger Einstieg. ValidateOnly lädt weder Spring-Kontext noch JDBC-Treiber/HTTP-Server. */
public final class ManualTestLauncher {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
    @AutoConfigurationPackage(basePackages = "de.mangalib")
    @ComponentScan("de.mangalib")
    static class ManualApplication {
        @org.springframework.context.annotation.Bean
        ManualBrowserPolicy manualBrowserPolicy() { return new ManualBrowserPolicy(); }
    }

    static boolean validateArguments(String[] args) {
        ManualTestGuard.require(args.length == 0 || Arrays.equals(args, new String[] { "--validate-only" }),
                "Unbekannte Startargumente; keine Konfigurationsargumente erlaubt.");
        return args.length == 1;
    }

    static ConfigurableEnvironment environment() {
        ManualTestGuard.rejectExternalConfiguration(System.getenv());
        ManualTestGuard.rejectExternalConfiguration(System.getProperties());
        var env = new StandardEnvironment();
        env.getPropertySources().addLast(new PropertiesPropertySource("manual", ManualTestGuard.settings()));
        return env;
    }

    public static void main(String[] args) {
        try {
            boolean validateOnly = validateArguments(args);
            var env = environment();
            verifyArtifact();
            ManualTestGuard.verify(env, System.getenv(ManualTestGuard.PASSWORD_VARIABLE));
            System.out.println("MANUELLE TESTUMGEBUNG: mangalib_manual_test / mangalib_manual_test_user; 127.0.0.1:18080; synthetische Scraper.");
            if (validateOnly) {
                System.out.println("Konfiguration geprüft; KEINE Datenbankverbindung und KEIN HTTP-Listener.");
                return;
            }
            var application = new SpringApplication(ManualApplication.class, ManualBannerAdvice.class);
            application.setEnvironment(env);
            application.setAddCommandLineProperties(false);
            application.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) event ->
                    ManualTestGuard.verify(event.getEnvironment(), System.getenv(ManualTestGuard.PASSWORD_VARIABLE)));
            application.addInitializers(ManualTestLauncher::registerGuard);
            application.run();
        } catch (Exception ignored) {
            // Niemals fremde Property-/Passwortwerte oder verschachtelte Bind-Exceptions ausgeben.
            System.err.println("Manueller Start abgebrochen: Artefakt, Passwortvariable oder Konfiguration nicht freigegeben.");
            System.exit(1);
        }
    }

    static void verifyArtifact() {
        verifyArtifact(ManualTestLauncher.class.getClassLoader());
    }

    static void verifyArtifact(ClassLoader loader) {
        for (String resource : new String[] { "application.properties", "de/mangalib/MangalibApplication.class",
                "de/mangalib/service/MangaScraper.class", "de/mangalib/service/BandScraper.class",
                "de/mangalib/service/LiveScraperGateway.class", "org/openqa/selenium/WebDriver.class" }) {
            ManualTestGuard.require(loader.getResource(resource) == null, "Live-/Standard-Artefakt erkannt.");
        }
        ManualTestGuard.require(loader.getResource("de/mangalibmanual/SyntheticScraperGateway.class") != null,
                "Scraper-Double fehlt.");
    }

    static void registerGuard(ConfigurableApplicationContext context) {
        var env = context.getEnvironment();
        ManualTestGuard.verify(env, System.getenv(ManualTestGuard.PASSWORD_VARIABLE));
        var factory = context.getBeanFactory();
        var registry = (BeanDefinitionRegistry) factory;
        var dataSource = new ManualTestDataSource(env);
        var definition = new RootBeanDefinition(ManualTestDataSource.class, () -> dataSource);
        registry.registerBeanDefinition("dataSource", definition);
        var scraper = new SyntheticScraperGateway();
        factory.registerSingleton("manualScraper", scraper);
        context.addBeanFactoryPostProcessor(beans -> {
            ManualTestGuard.verify(env, System.getenv(ManualTestGuard.PASSWORD_VARIABLE));
            ManualTestGuard.verifyBeans(beans, definition, scraper);
        });
        factory.addBeanPostProcessor(new BeanPostProcessor() {
            @Override public Object postProcessBeforeInitialization(Object bean, String name) {
                ManualTestGuard.require(!(bean instanceof DataSource) || bean == dataSource, "Fremde DataSource-Instanz.");
                ManualTestGuard.require(!(bean instanceof ScraperGateway) || bean == scraper, "Fremder Scraper.");
                return bean;
            }
        });
    }
}
