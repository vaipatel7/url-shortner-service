package com.urlshortener.repository;

import com.urlshortener.model.ClickEvent;
import com.urlshortener.model.DailyClickCount;
import com.urlshortener.model.DeviceTypeStat;
import com.urlshortener.model.mapper.ClickEventMapper;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RegisterRowMapper(ClickEventMapper.class)
public interface ClickEventRepository {

    /**
     * Record a click/redirect event for a short URL.
     * Returns the inserted row including DB-generated id and created_at.
     */
    @SqlUpdate("INSERT INTO click_events " +
               "(short_url_id, device_type, device_model, os, os_version, " +
               " browser, browser_version, user_agent, ip_address, clicked_at) " +
               "VALUES (:shortUrlId, :deviceType, :deviceModel, :os, :osVersion, " +
               "        :browser, :browserVersion, :userAgent, :ipAddress, :clickedAt)")
    @GetGeneratedKeys
    ClickEvent insert(@BindBean ClickEvent clickEvent);

    /**
     * Retrieve all click events for a given short URL (useful for analytics).
     */
    @SqlQuery("SELECT id, short_url_id, device_type, device_model, os, os_version, " +
              "       browser, browser_version, user_agent, ip_address, created_at, clicked_at " +
              "FROM click_events WHERE short_url_id = :shortUrlId " +
              "ORDER BY clicked_at DESC")
    List<ClickEvent> findByShortUrlId(@Bind("shortUrlId") Long shortUrlId);

    /**
     * Count click events for a given short URL.
     */
    @SqlQuery("SELECT COUNT(1) FROM click_events WHERE short_url_id = :shortUrlId")
    long countByShortUrlId(@Bind("shortUrlId") Long shortUrlId);

    /**
     * Total click count across all URLs.
     */
    @SqlQuery("SELECT COUNT(1) FROM click_events")
    long countAll();

    /**
     * Count clicks within a half-open time range [from, to).
     * Uses the idx_click_events_clicked_at index for efficient range scans.
     */
    @SqlQuery("SELECT COUNT(1) FROM click_events WHERE clicked_at >= :from AND clicked_at < :to")
    long countInRange(@Bind("from") Instant from, @Bind("to") Instant to);

    /**
     * Earliest recorded click timestamp — used to calculate total pagination pages.
     * Returns empty if no events exist.
     */
    @SqlQuery("SELECT MIN(clicked_at) FROM click_events")
    Optional<Instant> findEarliestClickedAt();

    /**
     * Daily click counts within a half-open time range [from, to).
     * Dates are normalised to UTC midnight.
     * Uses idx_click_events_clicked_at for the range filter.
     */
    @SqlQuery("SELECT date_trunc('day', clicked_at AT TIME ZONE 'UTC')::date AS date, " +
              "       COUNT(*) AS clicks " +
              "FROM click_events " +
              "WHERE clicked_at >= :from AND clicked_at < :to " +
              "GROUP BY 1 ORDER BY 1")
    List<DailyClickCount> dailyClicksInRange(@Bind("from") Instant from, @Bind("to") Instant to);

    /**
     * Device-type breakdown for all clicks since the given timestamp.
     * Uses the composite idx_click_events_device_type_clicked_at index.
     */
    @SqlQuery("SELECT COALESCE(device_type, 'UNKNOWN') AS device_type, COUNT(*) AS clicks " +
              "FROM click_events " +
              "WHERE clicked_at >= :since " +
              "GROUP BY device_type " +
              "ORDER BY clicks DESC")
    List<DeviceTypeStat> deviceTypeDistributionSince(@Bind("since") Instant since);
}
