package com.trading.platform.repository.mysql;

import com.trading.platform.model.mysql.Holding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByUserId(Long userId);

    // Row lock so two concurrent buys/sells of the same symbol cannot both act on a stale quantity.
    // Only call this from inside a @Transactional method.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Holding> findByUserIdAndSymbol(Long userId, String symbol);

    @Query("SELECT DISTINCT h.symbol FROM Holding h")
    List<String> findDistinctSymbols();

    @Query("SELECT DISTINCT h.userId FROM Holding h WHERE h.symbol = :symbol")
    List<Long> findDistinctUserIdsBySymbol(@Param("symbol") String symbol);

    @Query("SELECT DISTINCT h.userId FROM Holding h")
    List<Long> findDistinctUserIds();
}