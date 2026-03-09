package com.urlshortener.model.mapper;

import com.urlshortener.model.DeviceTypeStat;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DeviceTypeStatMapper implements RowMapper<DeviceTypeStat> {
    @Override
    public DeviceTypeStat map(ResultSet rs, StatementContext ctx) throws SQLException {
        return DeviceTypeStat.builder()
                .deviceType(rs.getString("device_type"))
                .clicks(rs.getLong("clicks"))
                .build();
    }
}
