package com.trading.platform.repository.mysql;

import com.trading.platform.model.mysql.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    List<Watchlist> findByUserIdOrderByCreatedAtAsc(Long userId);

    // Ownership check: a list id only resolves if it belongs to this user.
    Optional<Watchlist> findByIdAndUserId(Long id, Long userId);

    Optional<Watchlist> findByUserIdAndDefaultListTrue(Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    long countByUserId(Long userId);
}
