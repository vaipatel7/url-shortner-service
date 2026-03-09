package com.urlshortener;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import io.dropwizard.db.DataSourceFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class UrlShortenerConfiguration extends Configuration {

    @Valid
    @NotNull
    private DataSourceFactory database = new DataSourceFactory();

    /** Default domain used when the caller does not supply one. */
    @NotNull
    private String defaultDomain = "localhost:8080";

    @JsonProperty("database")
    public DataSourceFactory getDatabase() { return database; }

    @JsonProperty("database")
    public void setDatabase(DataSourceFactory database) { this.database = database; }

    @JsonProperty("defaultDomain")
    public String getDefaultDomain() { return defaultDomain; }

    @JsonProperty("defaultDomain")
    public void setDefaultDomain(String defaultDomain) { this.defaultDomain = defaultDomain; }
}
