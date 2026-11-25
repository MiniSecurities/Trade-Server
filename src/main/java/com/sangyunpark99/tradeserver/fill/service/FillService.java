package com.sangyunpark99.tradeserver.fill.service;

import com.sangyunpark99.tradeserver.fill.controller.dto.MyFillResponse;
import com.sangyunpark99.tradeserver.fill.repository.FillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FillService {

    private static final int RECENT_FILL_COUNT = 30;

    private final FillRepository fillRepository;

    public List<MyFillResponse> findMyFills(long userId) {
        return fillRepository.findMyFills(userId, Limit.of(RECENT_FILL_COUNT));
    }
}
