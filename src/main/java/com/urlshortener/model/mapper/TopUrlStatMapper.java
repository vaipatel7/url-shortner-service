package com.urlshortener.model.mapper;

import com.urlshortener.model.TopUrlStat;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class TopUrlStatMapper implements RowMapper<TopUrlStat> {
    @Override
    public TopUrlStat map(ResultSet rs, StatementContext ctx) throws SQLException {
        return TopUrlStat.builder()
                .alias(rs.getString("alias"))
                .longUrl(rs.getString("long_url"))
                .clicks(rs.getLong("clicks"))
                .build();
    }
}
