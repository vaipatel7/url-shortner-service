package com.urlshortener.model.mapper;

import com.urlshortener.model.ClickEvent;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class ClickEventMapper implements RowMapper<ClickEvent> {

    @Override
    public ClickEvent map(ResultSet rs, StatementContext ctx) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp clickedAt = rs.getTimestamp("clicked_at");
        return ClickEvent.builder()
                .id(rs.getLong("id"))
                .shortUrlId(rs.getLong("short_url_id"))
                .deviceType(rs.getString("device_type"))
                .deviceModel(rs.getString("device_model"))
                .os(rs.getString("os"))
                .osVersion(rs.getString("os_version"))
                .browser(rs.getString("browser"))
                .browserVersion(rs.getString("browser_version"))
                .userAgent(rs.getString("user_agent"))
                .ipAddress(rs.getString("ip_address"))
                .createdAt(createdAt != null ? createdAt.toInstant() : null)
                .clickedAt(clickedAt != null ? clickedAt.toInstant() : null)
                .build();
    }
}
