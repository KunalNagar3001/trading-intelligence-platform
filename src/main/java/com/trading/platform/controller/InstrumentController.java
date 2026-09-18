package com.trading.platform.controller;

import com.trading.platform.model.mysql.Instrument;
import com.trading.platform.service.InstrumentService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @GetMapping("/search")
    public List<Instrument> search(@RequestParam String q,
                                   @RequestParam(defaultValue = "10") int limit) {
        return instrumentService.search(q, limit);
    }
}