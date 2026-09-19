package com.trading.platform.repository.mysql;

import com.trading.platform.model.mysql.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByUserId(Long userId);

    @Query("SELECT DISTINCT h.symbol FROM Holding h")
    List<String> findDistinctSymbols();

    @Query("SELECT DISTINCT h.userId FROM Holding h WHERE h.symbol = :symbol")
    List<Long> findDistinctUserIdsBySymbol(@Param("symbol") String symbol);
}