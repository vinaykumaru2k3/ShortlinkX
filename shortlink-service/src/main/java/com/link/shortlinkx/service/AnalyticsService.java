package com.link.shortlinkx.service;

import com.link.shortlinkx.entity.Analytics;
import com.link.shortlinkx.repository.AnalyticsRepository;
import com.link.shortlinkx.repository.UrlRepository;
import com.link.shortlinkx.util.UserAgentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    // Must match the column sizes in V2__create_analytics_table.sql, otherwise the insert fails.
    private static final int MAX_IP = 45;
    private static final int MAX_REFERRER = 1024;
    private static final int MAX_USER_AGENT = 512;

    private final AnalyticsRepository analyticsRepository;
    private final UrlRepository urlRepository;

    @Async("taskExecutor")
    @Transactional
    public void recordClick(String shortCode, String ipAddress, String referrer, String userAgent) {
        log.debug("Recording click telemetry for shortCode: {}", shortCode);

        try {
            // 1. Increment click count in urls table
            urlRepository.incrementClickCount(shortCode);

            // 2. Save analytics trace record
            Analytics analytics = Analytics.builder()
                    .shortCode(shortCode)
                    .clickTimestamp(LocalDateTime.now())
                    .ipAddress(truncate(ipAddress != null ? ipAddress : "0.0.0.0", MAX_IP))
                    .referrer(truncate(referrer != null ? referrer : "Direct", MAX_REFERRER))
                    .userAgent(truncate(userAgent, MAX_USER_AGENT))
                    .operatingSystem(UserAgentParser.parseOS(userAgent))
                    .browser(UserAgentParser.parseBrowser(userAgent))
                    .build();

            analyticsRepository.save(analytics);
        } catch (Exception e) {
            log.error("Failed to save analytics or increment click count for shortCode: {}", shortCode, e);
        }
    }

    static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }
}
