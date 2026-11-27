package com.trading.platform.model.mysql;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * A named list of symbols owned by one user (for example "Banking" or "Long-term").
 * Every user has exactly one default list, created on first use.
 */
@Entity
@Table(name = "watchlists",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "name"}))
@Data
public class Watchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private boolean defaultList;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
