package com.trading.platform.dto.response;

import com.trading.platform.model.mysql.WatchlistItem;
import java.time.LocalDateTime;

public record WatchlistItemResponse(Long id, String symbol, LocalDateTime addedAt) {

    public static WatchlistItemResponse from(WatchlistItem item) {
        return new WatchlistItemResponse(item.getId(), item.getSymbol(), item.getAddedAt());
    }
}
