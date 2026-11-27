package com.trading.platform.controller;

import com.trading.platform.dto.request.WatchlistNameRequest;
import com.trading.platform.dto.request.WatchlistSymbolRequest;
import com.trading.platform.dto.response.WatchlistItemResponse;
import com.trading.platform.dto.response.WatchlistResponse;
import com.trading.platform.service.WatchlistManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/watchlists")
public class WatchlistController {

    private final WatchlistManagementService watchlistService;

    public WatchlistController(WatchlistManagementService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public List<WatchlistResponse> getWatchlists(Authentication authentication) {
        return watchlistService.getWatchlists(authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WatchlistResponse createWatchlist(Authentication authentication,
                                             @RequestBody WatchlistNameRequest request) {
        return watchlistService.createWatchlist(authentication.getName(), request.getName());
    }

    @PatchMapping("/{id}")
    public WatchlistResponse renameWatchlist(Authentication authentication, @PathVariable Long id,
                                             @RequestBody WatchlistNameRequest request) {
        return watchlistService.renameWatchlist(authentication.getName(), id, request.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWatchlist(Authentication authentication, @PathVariable Long id) {
        watchlistService.deleteWatchlist(authentication.getName(), id);
    }

    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public WatchlistItemResponse addSymbol(Authentication authentication, @PathVariable Long id,
                                           @RequestBody WatchlistSymbolRequest request) {
        return watchlistService.addSymbol(authentication.getName(), id, request.getSymbol());
    }

    @DeleteMapping("/{id}/items/{symbol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSymbol(Authentication authentication, @PathVariable Long id,
                             @PathVariable String symbol) {
        watchlistService.removeSymbol(authentication.getName(), id, symbol);
    }
}
