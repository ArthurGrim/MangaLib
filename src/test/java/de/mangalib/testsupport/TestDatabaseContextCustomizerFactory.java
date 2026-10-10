package de.mangalib.testsupport;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/** Automatischer Schutz für alle über Spring TestContext gestarteten Kontexte. */
public final class TestDatabaseContextCustomizerFactory implements ContextCustomizerFactory {
    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass,
            List<ContextConfigurationAttributes> configAttributes) {
        return DatabaseCustomizer.INSTANCE;
    }

    // Stabile Identität erlaubt die Wiederverwendung sicherer Spring-Testkontexte.
    private enum DatabaseCustomizer implements ContextCustomizer {
        INSTANCE;

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration configuration) {
            registerDatabaseGuard(context, () -> System.getenv(TestDatabaseGuard.PASSWORD_VARIABLE));
        }
    }

    // Die package-private Prüfstelle ermöglicht Lifecycle-Tests mit synthetischem Passwort.
    // Der registrierte Standard-Customizer und die JDBC-DataSource lesen weiterhin nur die Prozessvariable.
    static void registerDatabaseGuard(ConfigurableApplicationContext context, Supplier<String> password) {
        var environment = context.getEnvironment();
        var registry = (BeanDefinitionRegistry) context.getBeanFactory();
        var definition = new RootBeanDefinition(GuardedTestDataSource.class,
                () -> new GuardedTestDataSource(environment));
        registry.registerBeanDefinition("dataSource", definition);

        // Nach Verarbeitung der Konfiguration, aber vor Singleton-Erzeugung und
        // damit vor Hibernate, Schema-Validierung und SQL-Initialisierung prüfen.
        context.addBeanFactoryPostProcessor(beanFactory -> {
            TestDatabaseGuard.verify(environment, password.get());
            String[] names = beanFactory.getBeanNamesForType(DataSource.class, true, false);
            if (!Arrays.equals(names, new String[] { "dataSource" })
                    || beanFactory.getBeanDefinition("dataSource") != definition) {
                throw new IllegalStateException("Test-Datenbankschutz: Alternative DataSource erkannt.");
            }
        });
    }
}
