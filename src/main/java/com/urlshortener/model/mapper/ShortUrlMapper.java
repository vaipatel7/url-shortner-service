package com.urlshortener.model.mapper;

import com.urlshortener.model.AliasType;
import com.urlshortener.model.ShortUrl;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class ShortUrlMapper implements RowMapper<ShortUrl> {

    @Override
    public ShortUrl map(ResultSet rs, StatementContext ctx) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return ShortUrl.builder()
                .id(rs.getLong("id"))
                .createdAt(createdAt != null ? createdAt.toInstant() : null)
                .domain(rs.getString("domain"))
                .alias(rs.getString("alias"))
                .aliasType(AliasType.valueOf(rs.getString("alias_type")))
                .longUrl(rs.getString("long_url"))
                .build();
    }
}
