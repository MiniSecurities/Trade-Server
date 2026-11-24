package com.sangyunpark99.tradeserver.notification.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class EmitterRepositoryTest {

    private final EmitterRepository emitterRepository = new EmitterRepository();

    @Test
    @DisplayName("한 사용자가 여러 기기로 연결하면 모두 조회된다")
    void findsAllEmittersOfUser() {
        SseEmitter phone = new SseEmitter();
        SseEmitter laptop = new SseEmitter();

        emitterRepository.add(1L, phone);
        emitterRepository.add(1L, laptop);

        assertThat(emitterRepository.findAll(1L)).containsExactlyInAnyOrder(phone, laptop);
    }

    @Test
    @DisplayName("다른 사용자의 연결은 조회되지 않는다")
    void doesNotFindOtherUsersEmitters() {
        emitterRepository.add(1L, new SseEmitter());

        assertThat(emitterRepository.findAll(2L)).isEmpty();
    }

    @Test
    @DisplayName("연결 하나를 지우면 나머지 연결은 남는다")
    void removesOnlyGivenEmitter() {
        SseEmitter phone = new SseEmitter();
        SseEmitter laptop = new SseEmitter();
        emitterRepository.add(1L, phone);
        emitterRepository.add(1L, laptop);

        emitterRepository.remove(1L, phone);

        assertThat(emitterRepository.findAll(1L)).containsExactly(laptop);
    }

    @Test
    @DisplayName("마지막 연결을 지우면 사용자의 연결이 비어 있다")
    void removesLastEmitter() {
        SseEmitter phone = new SseEmitter();
        emitterRepository.add(1L, phone);

        emitterRepository.remove(1L, phone);

        assertThat(emitterRepository.findAll(1L)).isEmpty();
    }

    @Test
    @DisplayName("연결이 없는 사용자의 연결을 지워도 예외가 나지 않는다")
    void removeWithoutEmitters() {
        emitterRepository.remove(1L, new SseEmitter());

        assertThat(emitterRepository.findAll(1L)).isEmpty();
    }
}
