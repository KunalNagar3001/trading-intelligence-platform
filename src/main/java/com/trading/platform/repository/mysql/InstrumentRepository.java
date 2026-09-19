package com.trading.platform.repository.mysql;

import com.trading.platform.model.mysql.Instrument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface InstrumentRepository extends JpaRepository<Instrument, Long> {

    Optional<Instrument> findBySymbolAndActiveTrue(String symbol);

    List<Instrument> findByActiveTrue();

    @Query("""
           SELECT i FROM Instrument i
           WHERE i.active = true
             AND (UPPER(i.symbol) LIKE UPPER(CONCAT(:q, '%'))
                  OR UPPER(i.name) LIKE UPPER(CONCAT('%', :q, '%')))
           ORDER BY CASE WHEN UPPER(i.symbol) = UPPER(:q) THEN 0
                         WHEN UPPER(i.symbol) LIKE UPPER(CONCAT(:q, '%')) THEN 1
                         ELSE 2 END, i.symbol
           """)
    List<Instrument> search(@Param("q") String q, Pageable pageable);
}