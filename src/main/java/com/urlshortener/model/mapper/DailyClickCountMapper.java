package com.urlshortener.model.mapper;

import com.urlshortener.model.DailyClickCount;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public class DailyClickCountMapper implements RowMapper<DailyClickCount> {
    @Override
    public DailyClickCount map(ResultSet rs, StatementContext ctx) throws SQLException {
        return DailyClickCount.builder()
                .date(rs.getObject("date", LocalDate.class))
                .clicks(rs.getLong("clicks"))
                .build();
    }
}
