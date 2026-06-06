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

    private final AnalyticsRepository analyticsRepository;
    private final UrlRepository urlRepository;

    @Async
    @Transactional
    public void recordClick(String shortCode, String ipAddress, String referrer, String userAgent) {
        log.info("Asynchronously logging click telemetry and incrementing count for shortCode: {}", shortCode);
        
        try {
            // 1. Increment click count in urls table
            urlRepository.incrementClickCount(shortCode);

            // 2. Save analytics trace record
            Analytics analytics = Analytics.builder()
                    .shortCode(shortCode)
                    .clickTimestamp(LocalDateTime.now())
                    .ipAddress(ipAddress != null ? ipAddress : "0.0.0.0")
                    .referrer(referrer != null ? referrer : "Direct")
                    .userAgent(userAgent)
                    .operatingSystem(UserAgentParser.parseOS(userAgent))
                    .browser(UserAgentParser.parseBrowser(userAgent))
                    .build();

            analyticsRepository.save(analytics);
        } catch (Exception e) {
            log.error("Failed to asynchronously save analytics or increment click count for shortCode: {}", shortCode, e);
        }
    }
}
