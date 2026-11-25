package com.sangyunpark99.tradeserver.fill.controller;

import com.sangyunpark99.tradeserver.auth.UserContext;
import com.sangyunpark99.tradeserver.fill.controller.dto.MyFillResponse;
import com.sangyunpark99.tradeserver.fill.service.FillService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FillControllerTest {

    private final FillService fillService = mock(FillService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FillController(fillService)).build();
        UserContext.set(7L);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    @DisplayName("로그인한 사용자의 체결 목록을 200으로 돌려준다")
    void returnsMyFills() throws Exception {
        given(fillService.findMyFills(7L)).willReturn(List.of(
                new MyFillResponse("fill-1", 3L, "035720", 3, new BigDecimal("99699"),
                        LocalDateTime.of(2026, 11, 25, 11, 22, 13))));

        mockMvc.perform(get("/api/fills/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fillId").value("fill-1"))
                .andExpect(jsonPath("$[0].orderId").value(3))
                .andExpect(jsonPath("$[0].symbol").value("035720"))
                .andExpect(jsonPath("$[0].quantity").value(3))
                .andExpect(jsonPath("$[0].price").value(99699));

        verify(fillService).findMyFills(7L);
    }

    @Test
    @DisplayName("체결이 없으면 빈 배열을 돌려준다")
    void returnsEmptyArray() throws Exception {
        given(fillService.findMyFills(7L)).willReturn(List.of());

        mockMvc.perform(get("/api/fills/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
