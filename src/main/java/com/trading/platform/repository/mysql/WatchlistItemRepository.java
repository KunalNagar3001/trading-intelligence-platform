package com.trading.platform.repository.mysql;

import com.trading.platform.model.mysql.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

    List<WatchlistItem> findByWatchlistIdInOrderByAddedAtDesc(Collection<Long> watchlistIds);

    boolean existsByWatchlistIdAndSymbol(Long watchlistId, String symbol);

    long countByWatchlistId(Long watchlistId);

    long deleteByWatchlistIdAndSymbol(Long watchlistId, String symbol);

    long deleteByWatchlistId(Long watchlistId);

    // Every symbol on any watchlist: the price scheduler polls these.
    @Query("SELECT DISTINCT i.symbol FROM WatchlistItem i")
    List<String> findDistinctSymbols();

    // Distinct symbols across all of one user's lists (used for the per-user symbol cap).
    @Query("SELECT DISTINCT i.symbol FROM WatchlistItem i, Watchlist w "
            + "WHERE i.watchlistId = w.id AND w.userId = :userId")
    List<String> findDistinctSymbolsByUserId(@Param("userId") Long userId);
}
