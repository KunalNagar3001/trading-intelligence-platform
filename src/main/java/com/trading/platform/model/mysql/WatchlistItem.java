package com.trading.platform.model.mysql;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * One symbol inside one watchlist. A symbol can appear only once per list,
 * but the same symbol may be in several lists.
 */
@Entity
@Table(name = "watchlist_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"watchlist_id", "symbol"}))
@Data
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long watchlistId;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false)
    private LocalDateTime addedAt;
}
