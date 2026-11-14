package com.link.shortlinkx.controller;

import com.link.shortlinkx.dto.ShortUrlResponse;
import com.link.shortlinkx.exception.GlobalExceptionHandler;
import com.link.shortlinkx.exception.ResourceNotFoundException;
import com.link.shortlinkx.service.UrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UrlControllerTest {

    private UrlService urlService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        urlService = mock(UrlService.class);
        mvc = MockMvcBuilders.standaloneSetup(new UrlController(urlService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void redirectReturns302WithLocationAndRecordsClick() throws Exception {
        when(urlService.getOriginalUrlAndRecordClick(eq("abc123"), eq("203.0.113.9"), any(), any()))
                .thenReturn("https://example.com/page?x=1");

        mvc.perform(get("/api/v1/abc123")
                        .header("X-Forwarded-For", "203.0.113.9, 10.0.0.2")
                        .header("User-Agent", "JUnit"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/page?x=1"));
    }

    @Test
    void malformedForwardedForFallsBackToTheSocketAddress() throws Exception {
        when(urlService.getOriginalUrlAndRecordClick(anyString(), eq("127.0.0.1"), any(), any()))
                .thenReturn("https://example.com");

        mvc.perform(get("/api/v1/abc123").header("X-Forwarded-For", "'); DROP TABLE urls;--"))
                .andExpect(status().isFound());
    }

    @Test
    void unknownCodeIs404() throws Exception {
        when(urlService.getOriginalUrlAndRecordClick(eq("nope"), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Short URL not found for code: nope"));

        mvc.perform(get("/api/v1/nope")).andExpect(status().isNotFound());
    }

    @Test
    void codesWithUnexpectedCharactersNeverReachTheService() throws Exception {
        mvc.perform(get("/api/v1/abc-123")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/waytoolongcode1")).andExpect(status().isNotFound());

        verify(urlService, never()).getOriginalUrlAndRecordClick(anyString(), any(), any(), any());
    }

    @Test
    void shortenRequiresTheIdentityHeaderFromTheGateway() throws Exception {
        mvc.perform(post("/api/v1/shorten").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shortenRejectsNonHttpSchemesAndOverlongUrls() throws Exception {
        for (String body : List.of("{\"url\":\"javascript:alert(1)\"}", "{\"url\":\"ftp://example.com/x\"}",
                "{\"url\":\"\"}", "{\"url\":\"https://example.com/" + "a".repeat(2100) + "\"}")) {
            mvc.perform(post("/api/v1/shorten").header("X-User-Id", "1")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void shortenReturnsTheNewLink() throws Exception {
        when(urlService.shortenUrl(any(), eq(5L))).thenReturn(
                new ShortUrlResponse("http://localhost:8080/api/v1/abc123", "https://example.com", "abc123", 0L));

        mvc.perform(post("/api/v1/shorten").header("X-User-Id", "5")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc123"));
    }

    @Test
    void historyIsPaginated() throws Exception {
        when(urlService.getUrlsByUserId(5L, 2, 10)).thenReturn(List.of());

        mvc.perform(get("/api/v1/urls").header("X-User-Id", "5").param("page", "2").param("size", "10"))
                .andExpect(status().isOk());
        verify(urlService).getUrlsByUserId(5L, 2, 10);
    }

    @Test
    void unexpectedErrorsDoNotLeakInternals() throws Exception {
        when(urlService.getUrlsByUserId(any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new IllegalStateException("could not execute statement; SQL [select secret from users]"));

        mvc.perform(get("/api/v1/urls").header("X-User-Id", "5"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
