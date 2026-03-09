package com.urlshortener.repository;

import com.urlshortener.model.ShortUrl;
import com.urlshortener.model.mapper.ShortUrlMapper;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Optional;

@RegisterRowMapper(ShortUrlMapper.class)
public interface UrlRepository {

    /**
     * Insert a new short URL record.
     * Returns the full inserted row (including DB-generated id and created_at).
     */
    @SqlUpdate("INSERT INTO short_urls (domain, alias, alias_type, long_url) " +
               "VALUES (:domain, :alias, :aliasType, :longUrl)")
    @GetGeneratedKeys
    ShortUrl insert(@BindBean ShortUrl shortUrl);

    /**
     * Look up a short URL by its alias/short-code.
     */
    @SqlQuery("SELECT id, created_at, domain, alias, alias_type, long_url " +
              "FROM short_urls WHERE alias = :alias")
    Optional<ShortUrl> findByAlias(@Bind("alias") String alias);

    /**
     * Look up a short URL by its primary key.
     */
    @SqlQuery("SELECT id, created_at, domain, alias, alias_type, long_url " +
              "FROM short_urls WHERE id = :id")
    Optional<ShortUrl> findById(@Bind("id") Long id);

    /**
     * Check whether a given alias is already taken.
     */
    @SqlQuery("SELECT COUNT(1) FROM short_urls WHERE alias = :alias")
    int countByAlias(@Bind("alias") String alias);

    /**
     * Returns a page of records ordered by creation time descending.
     *
     * @param limit  number of rows per page (always 10)
     * @param offset zero-based row offset  (page * limit)
     */
    @SqlQuery("SELECT id, created_at, domain, alias, alias_type, long_url " +
              "FROM short_urls ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    List<ShortUrl> findAll(@Bind("limit") int limit, @Bind("offset") int offset);

    /**
     * Total row count used to compute totalPages on the response.
     */
    @SqlQuery("SELECT COUNT(1) FROM short_urls")
    long countAll();
}
