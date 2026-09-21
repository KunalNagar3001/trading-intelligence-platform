package com.trading.platform.model.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "stock_dna")
@Data
public class StockDna {

    @Id
    private String id;

    private String symbol;
    private List<CanonicalMoment> canonicalMoments = new ArrayList<>();
}