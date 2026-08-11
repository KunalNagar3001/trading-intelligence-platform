package com.trading.platform.repository.mysql;

import com.trading.platform.model.mysql.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByUserId(Long userId);
}