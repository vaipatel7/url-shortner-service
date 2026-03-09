package com.urlshortener;

import com.urlshortener.filter.CorsFilter;
import com.urlshortener.model.mapper.DailyClickCountMapper;
import com.urlshortener.model.mapper.DeviceTypeStatMapper;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.resource.RedirectResource;
import com.urlshortener.resource.v1.AnalyticsResource;
import com.urlshortener.resource.v1.UrlResource;
import com.urlshortener.service.v1.AnalyticsService;
import com.urlshortener.service.v1.UrlService;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.jdbi3.JdbiFactory;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;

public class UrlShortenerApplication extends Application<UrlShortenerConfiguration> {

    public static void main(String[] args) throws Exception {
        new UrlShortenerApplication().run(args);
    }

    @Override
    public String getName() {
        return "url-shortener-service";
    }

    @Override
    public void initialize(Bootstrap<UrlShortenerConfiguration> bootstrap) {
        // Enable ${ENV_VAR:-default} substitution in config.yml
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(
                        bootstrap.getConfigurationSourceProvider(),
                        new EnvironmentVariableSubstitutor(false) // false = don't throw on missing vars
                )
        );
    }

    @Override
    public void run(UrlShortenerConfiguration configuration, Environment environment) {
        // 1. Run Flyway migrations before any DB access
        runFlywayMigrations(configuration);

        // 2. Build JDBI from the managed DataSource
        final JdbiFactory factory = new JdbiFactory();
        final Jdbi jdbi = factory.build(environment, configuration.getDatabase(), "postgresql");

        // 3. Register row mappers for analytics result types
        jdbi.registerRowMapper(new DailyClickCountMapper());
        jdbi.registerRowMapper(new DeviceTypeStatMapper());

        // 4. Wire up repositories
        final UrlRepository urlRepository = jdbi.onDemand(UrlRepository.class);
        final ClickEventRepository clickEventRepository = jdbi.onDemand(ClickEventRepository.class);

        // 5. Build services (AnalyticsService must be constructed before UrlService)
        final AnalyticsService analyticsService = new AnalyticsService(clickEventRepository);
        final UrlService urlService = new UrlService(urlRepository, analyticsService, configuration.getDefaultDomain());

        // 6. Register AnalyticsService lifecycle so its executor shuts down cleanly
        environment.lifecycle().manage(analyticsService);

        // 7. Register REST resources and CORS filter
        environment.jersey().register(new UrlResource(urlService));
        environment.jersey().register(new RedirectResource(urlService, analyticsService));
        environment.jersey().register(new AnalyticsResource(analyticsService));
        environment.jersey().register(CorsFilter.class);
    }

    private void runFlywayMigrations(UrlShortenerConfiguration configuration) {
        Flyway flyway = Flyway.configure()
                .dataSource(
                        configuration.getDatabase().getUrl(),
                        configuration.getDatabase().getUser(),
                        configuration.getDatabase().getPassword()
                )
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
    }
}
