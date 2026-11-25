package com.sangyunpark99.tradeserver.notification.controller;

import com.sangyunpark99.tradeserver.auth.UserContext;
import com.sangyunpark99.tradeserver.notification.repository.EmitterRepository;
import com.sangyunpark99.tradeserver.notification.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

class NotificationControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        NotificationService notificationService = new NotificationService(new EmitterRepository());
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService)).build();
        UserContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    private String subscribe(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(request().asyncStarted())
                .andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("처음 구독하면 connected만 보내고 resync는 보내지 않는다")
    void firstSubscribeSendsOnlyConnected() throws Exception {
        String body = subscribe(get("/api/notifications/subscribe"));

        assertThat(body).contains("event:connected");
        assertThat(body).doesNotContain("event:resync");
    }

    @Test
    @DisplayName("connected 이벤트에 id가 있어 재연결 때 Last-Event-ID로 돌아온다")
    void connectedHasId() throws Exception {
        String body = subscribe(get("/api/notifications/subscribe"));

        assertThat(body).contains("id:connected");
    }

    @Test
    @DisplayName("Last-Event-ID가 있으면 connected 다음에 데이터가 있는 resync를 보낸다")
    void resubscribeSendsResync() throws Exception {
        String body = subscribe(get("/api/notifications/subscribe").header("Last-Event-ID", "fill-1"));

        assertThat(body).contains("event:connected");
        int resync = body.indexOf("event:resync");
        assertThat(resync).isGreaterThan(body.indexOf("event:connected"));
        assertThat(body.substring(resync)).contains("data:");
    }

    @Test
    @DisplayName("Last-Event-ID가 비어 있으면 resync를 보내지 않는다")
    void blankLastEventIdIsFirstSubscribe() throws Exception {
        String body = subscribe(get("/api/notifications/subscribe").header("Last-Event-ID", " "));

        assertThat(body).contains("event:connected");
        assertThat(body).doesNotContain("event:resync");
    }
}
