package com.trading.platform.dto.response;

import com.trading.platform.model.mysql.Watchlist;
import com.trading.platform.model.mysql.WatchlistItem;
import java.time.LocalDateTime;
import java.util.List;

public record WatchlistResponse(Long id, String name, boolean defaultList,
                                LocalDateTime createdAt, List<WatchlistItemResponse> items) {

    public static WatchlistResponse from(Watchlist list, List<WatchlistItem> items) {
        return new WatchlistResponse(list.getId(), list.getName(), list.isDefaultList(),
                list.getCreatedAt(), items.stream().map(WatchlistItemResponse::from).toList());
    }
}
