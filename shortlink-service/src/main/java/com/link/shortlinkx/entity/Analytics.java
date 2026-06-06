package com.link.shortlinkx.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "analytics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Analytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, length = 10)
    private String shortCode;

    @Column(name = "click_timestamp", nullable = false)
    private LocalDateTime clickTimestamp;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(length = 1024)
    private String referrer;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "operating_system", length = 50)
    private String operatingSystem;

    @Column(length = 50)
    private String browser;
}
