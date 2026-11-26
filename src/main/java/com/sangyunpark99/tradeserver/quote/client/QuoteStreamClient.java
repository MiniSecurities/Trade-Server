package com.sangyunpark99.tradeserver.quote.client;

import com.sangyunpark99.tradeserver.quote.dto.Quote;
import com.sangyunpark99.tradeserver.quote.service.QuotePublisher;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuoteStreamClient {

    private static final long RECONNECT_DELAY_MILLIS = 1_000;
    private static final String QUOTE_EVENT = "quote";

    private final RestClient quoteStreamRestClient;
    private final JsonMapper jsonMapper;
    private final QuotePublisher quotePublisher;

    private volatile boolean running = true;
    private Thread thread;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        thread = Thread.ofPlatform()
                .name("quote-stream")
                .start(this::run);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void run() {
        while (running) {
            connect();

            if (!running) {
                break;
            }

            log.info("시세 스트림 재연결 대기 {}ms", RECONNECT_DELAY_MILLIS);

            try {
                Thread.sleep(RECONNECT_DELAY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("시세 스트림 스레드 종료");
    }

    private void connect() {
        try {
            quoteStreamRestClient.get()
                    .uri("/api/quotes/stream")
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            log.warn("시세 스트림 연결 실패 status={}", response.getStatusCode());
                            return null;
                        }

                        log.info("시세 스트림 연결됨");

                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            read(reader);
                        }

                        return null;
                    });
            log.warn("시세 스트림 종료");
        } catch (RestClientException e) {
            log.error("시세 스트림 연결 끊김: {}", e.getMessage());
        }
    }

    private void read(BufferedReader reader) throws IOException {
        String event = null;
        StringBuilder data = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (QUOTE_EVENT.equals(event) && !data.isEmpty()) {
                    handleQuote(data.toString());
                }
                event = null;
                data.setLength(0);
            } else if (line.startsWith("event:")) {
                event = valueOf(line, "event:");
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(valueOf(line, "data:"));
            }
        }
    }

    private String valueOf(String line, String field) {
        String value = line.substring(field.length());
        return value.startsWith(" ") ? value.substring(1) : value;
    }

    private void handleQuote(String json) {
        try {
            Quote quote = jsonMapper.readValue(json, Quote.class);
            log.debug("[시세] {}", quote);
            quotePublisher.publish(quote);
        } catch (JacksonException e) {
            log.warn("시세 파싱 실패 data={}", json, e);
        }
    }
}
