package com.urlshortener.service.v1;

import com.urlshortener.dto.v1.AnalyticsPagination;
import com.urlshortener.dto.v1.AnalyticsResponse;
import com.urlshortener.dto.v1.AnalyticsSummary;
import com.urlshortener.dto.v1.TimeseriesPoint;
import com.urlshortener.model.ClickEvent;
import com.urlshortener.model.DailyClickCount;
import com.urlshortener.model.DeviceTypeStat;
import com.urlshortener.model.ShortUrl;
import com.urlshortener.model.TopUrlStat;
import com.urlshortener.repository.ClickEventRepository;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua_parser.Client;
import ua_parser.Parser;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Handles both async click recording (for redirect performance) and synchronous
 * analytics queries (timeseries, device distribution, totals).
 *
 * <p>Implements {@link Managed} so Dropwizard shuts down the executor gracefully.</p>
 */
public class AnalyticsService implements Managed {

    private static final Logger LOG = LoggerFactory.getLogger(AnalyticsService.class);
    private static final int THREAD_POOL_SIZE = 4;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;
    private static final int TIMESERIES_DAYS = 7;
    private static final int DEVICE_DISTRIBUTION_DAYS = 30;

    private final ClickEventRepository clickEventRepository;
    private final Parser uaParser;
    private final ExecutorService executor;

    public AnalyticsService(ClickEventRepository clickEventRepository) {
        this.clickEventRepository = clickEventRepository;
        this.uaParser = new Parser();
        AtomicInteger counter = new AtomicInteger(1);
        this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE, r -> {
            Thread t = new Thread(r, "analytics-worker-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
    }

    // -------------------------------------------------------------------------
    // Click recording (async)
    // -------------------------------------------------------------------------

    /**
     * Submits the click-recording work to the thread pool and returns immediately.
     * Any exception during DB write is logged but does not affect the redirect.
     */
    public void recordClickAsync(ShortUrl shortUrl, String userAgentHeader, String ipAddress) {
        executor.submit(() -> {
            try {
                recordClick(shortUrl, userAgentHeader, ipAddress);
            } catch (Exception e) {
                LOG.error("Failed to record click for alias '{}': {}",
                        shortUrl.getAlias(), e.getMessage(), e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Analytics queries (synchronous)
    // -------------------------------------------------------------------------

    /**
     * Click count for a single short URL. Used by UrlResource when listing URLs.
     */
    public long getClickCount(Long shortUrlId) {
        return clickEventRepository.countByShortUrlId(shortUrlId);
    }

    /**
     * Total click count across all URLs.
     */
    public long getTotalClickCount() {
        return clickEventRepository.countAll();
    }

    /**
     * Paginated 7-day analytics windows.
     *
     * <ul>
     *   <li>page 0 → last 7 days</li>
     *   <li>page 1 → 7–14 days ago</li>
     *   <li>page N → N*7 – (N+1)*7 days ago</li>
     * </ul>
     */
    public AnalyticsResponse getAnalytics(int page) {
        // Align to UTC calendar-day boundaries so the 7-point timeseries always covers
        // 7 complete days ending at the close of today (i.e. start of tomorrow UTC).
        // Using Instant.now() as the upper bound would cause the window to span 8 calendar
        // dates, dropping today's data from the generated timeseries labels.
        Instant now = Instant.now();
        ZonedDateTime windowEnd = LocalDate.now(ZoneOffset.UTC)
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .minusDays((long) TIMESERIES_DAYS * page);
        Instant currentEnd    = windowEnd.toInstant();
        Instant currentStart  = windowEnd.minusDays(TIMESERIES_DAYS).toInstant();
        Instant previousStart = currentStart.minus(Duration.ofDays(TIMESERIES_DAYS));

        long currentClicks  = clickEventRepository.countInRange(currentStart, currentEnd);
        long previousClicks = clickEventRepository.countInRange(previousStart, currentStart);

        double percentChange;
        if (previousClicks == 0) {
            percentChange = currentClicks > 0 ? 100.0 : 0.0;
        } else {
            percentChange = Math.round(
                    ((double) (currentClicks - previousClicks) / previousClicks) * 10_000.0
            ) / 100.0;
        }

        List<DailyClickCount> raw = clickEventRepository.dailyClicksInRange(currentStart, currentEnd);
        List<TimeseriesPoint> timeseries = buildTimeseries(currentStart, raw);

        int totalPages = clickEventRepository.findEarliestClickedAt()
                .map(earliest -> (int) Math.ceil(
                        (double) Duration.between(earliest, now).toDays() / TIMESERIES_DAYS))
                .map(p -> Math.max(p, 1))
                .orElse(1);

        return AnalyticsResponse.builder()
                .summary(AnalyticsSummary.builder()
                        .currentPeriodClicks(currentClicks)
                        .previousPeriodClicks(previousClicks)
                        .percentChange(percentChange)
                        .build())
                .timeseries(timeseries)
                .pagination(AnalyticsPagination.builder()
                        .currentPage(page)
                        .totalPages(totalPages)
                        .build())
                .build();
    }

    /**
     * Device-type distribution for the last 30 days.
     */
    public List<DeviceTypeStat> getDeviceTypeDistribution() {
        Instant since = Instant.now().minus(Duration.ofDays(DEVICE_DISTRIBUTION_DAYS));
        return clickEventRepository.deviceTypeDistributionSince(since);
    }

    /**
     * Top 10 URLs by click count for today (UTC calendar day).
     */
    public List<TopUrlStat> getTopUrlsToday() {
        ZonedDateTime todayUtc = ZonedDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC);
        Instant todayStart = todayUtc.toInstant();
        Instant tomorrowStart = todayUtc.plusDays(1).toInstant();
        return clickEventRepository.topUrlsInRange(todayStart, tomorrowStart);
    }

    // -------------------------------------------------------------------------
    // Managed lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void start() {
        // Executor is initialised at construction time; nothing to do here.
    }

    @Override
    public void stop() throws InterruptedException {
        LOG.info("Shutting down AnalyticsService executor …");
        executor.shutdown();
        if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            LOG.warn("AnalyticsService executor did not terminate within {} s — forcing shutdown",
                    SHUTDOWN_TIMEOUT_SECONDS);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<TimeseriesPoint> buildTimeseries(Instant periodStart, List<DailyClickCount> raw) {
        Map<LocalDate, Long> byDate = raw.stream()
                .collect(Collectors.toMap(DailyClickCount::getDate, DailyClickCount::getClicks));

        LocalDate startDate = periodStart.atZone(ZoneOffset.UTC).toLocalDate();
        List<TimeseriesPoint> result = new ArrayList<>(TIMESERIES_DAYS);
        for (int i = 0; i < TIMESERIES_DAYS; i++) {
            LocalDate date = startDate.plusDays(i);
            result.add(TimeseriesPoint.builder()
                    .date(date.toString())
                    .clicks(byDate.getOrDefault(date, 0L))
                    .build());
        }
        return result;
    }

    private void recordClick(ShortUrl shortUrl, String userAgentHeader, String ipAddress) {
        String safeUa = userAgentHeader != null ? userAgentHeader : "";
        Client client = uaParser.parse(safeUa);

        ClickEvent event = ClickEvent.builder()
                .shortUrlId(shortUrl.getId())
                .deviceType(resolveDeviceType(client))
                .deviceModel(client.device.family)
                .os(client.os.family)
                .osVersion(joinVersion(client.os.major, client.os.minor))
                .browser(client.userAgent.family)
                .browserVersion(joinVersion(client.userAgent.major, client.userAgent.minor))
                .userAgent(safeUa)
                .ipAddress(ipAddress != null ? ipAddress : "unknown")
                .clickedAt(Instant.now())
                .build();

        clickEventRepository.insert(event);
        LOG.debug("Recorded click for alias '{}' from ip={} browser={} os={}",
                shortUrl.getAlias(), event.getIpAddress(), event.getBrowser(), event.getOs());
    }

    private String resolveDeviceType(Client client) {
        String family = client.device.family != null ? client.device.family.toLowerCase() : "";
        if (family.contains("spider") || family.contains("bot")) return "BOT";
        if (family.contains("phone") || family.contains("mobile")
                || family.equals("generic smartphone"))       return "MOBILE";
        if (family.contains("tablet") || family.contains("ipad")) return "TABLET";
        return "DESKTOP";
    }

    private String joinVersion(String major, String minor) {
        if (major == null) return null;
        return minor == null ? major : major + "." + minor;
    }
}
