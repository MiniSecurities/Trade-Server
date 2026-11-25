package com.sangyunpark99.tradeserver.notification.service;

import com.sangyunpark99.tradeserver.notification.FillAppliedEvent;
import com.sangyunpark99.tradeserver.notification.FillNotification;
import com.sangyunpark99.tradeserver.notification.repository.EmitterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final long TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

    private final EmitterRepository emitterRepository;

    public SseEmitter subscribe(long userId, String lastEventId) {
        SseEmitter sseEmitter = new SseEmitter(TIMEOUT_MILLIS);

        emitterRepository.add(userId, sseEmitter);

        sseEmitter.onCompletion(() -> emitterRepository.remove(userId, sseEmitter));
        sseEmitter.onTimeout(() -> emitterRepository.remove(userId, sseEmitter));
        sseEmitter.onError(e -> emitterRepository.remove(userId, sseEmitter));

        try {
            sseEmitter.send(SseEmitter.event().name("connected").data("연결됨"));
            if(StringUtils.hasText(lastEventId)) {
                sseEmitter.send(SseEmitter.event().name("resync").data("체결을 다시 조회하세요"));
            }
        } catch (IOException e) {
            emitterRepository.remove(userId, sseEmitter);
        }

        return sseEmitter;
    }

    public void sendFill(FillAppliedEvent event) {
        Set<SseEmitter> userEmitters = emitterRepository.findAll(event.userId());
        FillNotification notification = FillNotification.from(event);

        for(SseEmitter emitter: userEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("fill")
                        .id(event.fillId())
                        .data(notification, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                emitterRepository.remove(event.userId(), emitter);
            }
        }
    }
}