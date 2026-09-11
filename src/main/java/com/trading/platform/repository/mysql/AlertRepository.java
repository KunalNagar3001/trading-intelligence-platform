package com.trading.platform.repository.mysql;

import com.trading.platform.model.mysql.PriceAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<PriceAlert, Long> {

    List<PriceAlert> findByUserId(Long userId);

    List<PriceAlert> findByUserIdAndActiveTrue(Long userId);

    List<PriceAlert> findBySymbolAndActiveTrue(String symbol);
}