package com.sangyunpark99.tradeserver.notification.service;

import com.sangyunpark99.tradeserver.notification.FillAppliedEvent;
import com.sangyunpark99.tradeserver.notification.FillNotification;
import com.sangyunpark99.tradeserver.notification.repository.EmitterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceTest {

    private final EmitterRepository emitterRepository = new EmitterRepository();
    private final NotificationService notificationService = new NotificationService(emitterRepository);

    private static FillAppliedEvent event(long userId) {
        return new FillAppliedEvent(userId, "fill-1", 10L, "KAKAO", 3, new BigDecimal("99699"),
                LocalDateTime.of(2026, 11, 24, 20, 27, 29));
    }

    @Test
    @DisplayName("구독하면 사용자의 연결로 저장된다")
    void subscribeStoresEmitter() {
        SseEmitter emitter = notificationService.subscribe(1L, null);

        assertThat(emitterRepository.findAll(1L)).containsExactly(emitter);
    }

    @Test
    @DisplayName("재구독해도 사용자의 연결로 저장된다")
    void resubscribeStoresEmitter() {
        SseEmitter emitter = notificationService.subscribe(1L, "fill-1");

        assertThat(emitterRepository.findAll(1L)).containsExactly(emitter);
    }

    @Test
    @DisplayName("체결 알림은 그 사용자의 모든 연결에 한 번씩 간다")
    void sendsFillToAllEmittersOfUser() {
        RecordingEmitter phone = new RecordingEmitter();
        RecordingEmitter laptop = new RecordingEmitter();
        emitterRepository.add(1L, phone);
        emitterRepository.add(1L, laptop);

        notificationService.sendFill(event(1L));

        FillNotification expected = FillNotification.from(event(1L));
        assertThat(phone.notifications).containsExactly(expected);
        assertThat(laptop.notifications).containsExactly(expected);
    }

    @Test
    @DisplayName("다른 사용자의 연결에는 체결 알림이 가지 않는다")
    void doesNotSendToOtherUser() {
        RecordingEmitter other = new RecordingEmitter();
        emitterRepository.add(2L, other);

        notificationService.sendFill(event(1L));

        assertThat(other.notifications).isEmpty();
    }

    @Test
    @DisplayName("전송에 실패한 연결은 지우고 나머지 연결에는 보낸다")
    void removesFailedEmitter() {
        SseEmitter broken = new FailingEmitter();
        RecordingEmitter alive = new RecordingEmitter();
        emitterRepository.add(1L, broken);
        emitterRepository.add(1L, alive);

        notificationService.sendFill(event(1L));

        assertThat(emitterRepository.findAll(1L)).containsExactly(alive);
        assertThat(alive.notifications).hasSize(1);
    }

    @Test
    @DisplayName("이미 끝난 연결은 예외 없이 지운다")
    void removesCompletedEmitter() {
        SseEmitter completed = new SseEmitter();
        completed.complete();
        emitterRepository.add(1L, completed);

        notificationService.sendFill(event(1L));

        assertThat(emitterRepository.findAll(1L)).isEmpty();
    }

    private static class RecordingEmitter extends SseEmitter {

        private final List<FillNotification> notifications = new ArrayList<>();

        @Override
        public void send(SseEventBuilder builder) {
            builder.build().stream()
                    .map(DataWithMediaType::getData)
                    .filter(FillNotification.class::isInstance)
                    .map(FillNotification.class::cast)
                    .forEach(notifications::add);
        }
    }

    private static class FailingEmitter extends SseEmitter {

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            throw new IOException("연결 끊김");
        }
    }
}
