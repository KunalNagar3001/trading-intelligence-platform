package com.trading.platform.service;

import com.trading.platform.dto.response.WatchlistItemResponse;
import com.trading.platform.dto.response.WatchlistResponse;
import com.trading.platform.exception.DuplicateWatchListItemException;
import com.trading.platform.exception.DuplicateWatchlistNameException;
import com.trading.platform.exception.UserNotFoundException;
import com.trading.platform.exception.WatchListItemNotFoundException;
import com.trading.platform.exception.WatchlistNotFoundException;
import com.trading.platform.model.mysql.Watchlist;
import com.trading.platform.model.mysql.WatchlistItem;
import com.trading.platform.repository.mysql.UserRepository;
import com.trading.platform.repository.mysql.WatchlistItemRepository;
import com.trading.platform.repository.mysql.WatchlistRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creating, renaming and deleting a user's watchlists, and adding and removing symbols in them.
 * Every list is looked up through its owner, so a user can never reach another user's list; a miss
 * is reported as "not found". Each user has exactly one default list, created the first time they
 * touch their watchlists. (WatchlistService is the read side used by the WebSocket.)
 */
@Service
public class WatchlistManagementService {

    static final String DEFAULT_LIST_NAME = "Default";
    static final int MAX_LISTS_PER_USER = 10;
    static final int MAX_SYMBOLS_PER_LIST = 20;
    // Each distinct symbol costs one Yahoo call per cycle, so the cap is on distinct symbols per user.
    static final int MAX_DISTINCT_SYMBOLS_PER_USER = 50;
    static final int MAX_NAME_LENGTH = 50;

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final UserRepository userRepository;
    private final InstrumentService instrumentService;
    private final MarketDataService marketDataService;

    @Value("${app.market-data.mode:simulated}")
    private String marketDataMode;

    public WatchlistManagementService(WatchlistRepository watchlistRepository,
                                      WatchlistItemRepository watchlistItemRepository,
                                      UserRepository userRepository,
                                      InstrumentService instrumentService,
                                      MarketDataService marketDataService) {
        this.watchlistRepository = watchlistRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.userRepository = userRepository;
        this.instrumentService = instrumentService;
        this.marketDataService = marketDataService;
    }

    // ---- lists ----

    public List<WatchlistResponse> getWatchlists(String email) {
        Long userId = userIdFor(email);
        ensureDefaultList(userId);

        List<Watchlist> lists = watchlistRepository.findByUserIdOrderByCreatedAtAsc(userId);
        List<Long> ids = lists.stream().map(Watchlist::getId).toList();
        Map<Long, List<WatchlistItem>> itemsByList = watchlistItemRepository
                .findByWatchlistIdInOrderByAddedAtDesc(ids).stream()
                .collect(Collectors.groupingBy(WatchlistItem::getWatchlistId));

        return lists.stream()
                .map(list -> WatchlistResponse.from(list, itemsByList.getOrDefault(list.getId(), List.of())))
                .toList();
    }

    public WatchlistResponse createWatchlist(String email, String rawName) {
        Long userId = userIdFor(email);
        String name = normaliseName(rawName);
        ensureDefaultList(userId);

        if (watchlistRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw duplicateName(name);
        }
        if (watchlistRepository.countByUserId(userId) >= MAX_LISTS_PER_USER) {
            throw new IllegalArgumentException("You can have at most " + MAX_LISTS_PER_USER + " watchlists");
        }

        Watchlist list = new Watchlist();
        list.setUserId(userId);
        list.setName(name);
        list.setDefaultList(false);
        list.setCreatedAt(LocalDateTime.now());
        try {
            return WatchlistResponse.from(watchlistRepository.saveAndFlush(list), List.of());
        } catch (DataIntegrityViolationException e) {
            throw duplicateName(name);
        }
    }

    public WatchlistResponse renameWatchlist(String email, Long watchlistId, String rawName) {
        Long userId = userIdFor(email);
        Watchlist list = ownedList(userId, watchlistId);
        String name = normaliseName(rawName);

        if (!name.equalsIgnoreCase(list.getName())
                && watchlistRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw duplicateName(name);
        }
        list.setName(name);
        try {
            Watchlist saved = watchlistRepository.saveAndFlush(list);
            return WatchlistResponse.from(saved,
                    watchlistItemRepository.findByWatchlistIdInOrderByAddedAtDesc(List.of(saved.getId())));
        } catch (DataIntegrityViolationException e) {
            throw duplicateName(name);
        }
    }

    @Transactional
    public void deleteWatchlist(String email, Long watchlistId) {
        Long userId = userIdFor(email);
        Watchlist list = ownedList(userId, watchlistId);
        if (list.isDefaultList()) {
            throw new IllegalArgumentException("The default watchlist can't be deleted");
        }
        // No database foreign keys in this project, so remove the items explicitly.
        watchlistItemRepository.deleteByWatchlistId(list.getId());
        watchlistRepository.delete(list);
    }

    // ---- symbols ----

    public WatchlistItemResponse addSymbol(String email, Long watchlistId, String rawSymbol) {
        Long userId = userIdFor(email);
        Watchlist list = ownedList(userId, watchlistId);
        String symbol = normaliseSymbol(rawSymbol);

        // Cheap database checks first, the network call last.
        if (watchlistItemRepository.existsByWatchlistIdAndSymbol(list.getId(), symbol)) {
            throw new DuplicateWatchListItemException(symbol + " is already in \"" + list.getName() + "\"");
        }
        if (watchlistItemRepository.countByWatchlistId(list.getId()) >= MAX_SYMBOLS_PER_LIST) {
            throw new IllegalArgumentException(
                    "A watchlist can hold at most " + MAX_SYMBOLS_PER_LIST + " symbols");
        }
        Set<String> userSymbols = new HashSet<>(watchlistItemRepository.findDistinctSymbolsByUserId(userId));
        if (!userSymbols.contains(symbol) && userSymbols.size() >= MAX_DISTINCT_SYMBOLS_PER_USER) {
            throw new IllegalArgumentException("You can follow at most " + MAX_DISTINCT_SYMBOLS_PER_USER
                    + " different symbols across all your watchlists");
        }
        validateSymbol(symbol);

        WatchlistItem item = new WatchlistItem();
        item.setWatchlistId(list.getId());
        item.setSymbol(symbol);
        item.setAddedAt(LocalDateTime.now());
        try {
            // Two identical requests can both pass the exists check; the unique constraint decides.
            return WatchlistItemResponse.from(watchlistItemRepository.saveAndFlush(item));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateWatchListItemException(symbol + " is already in \"" + list.getName() + "\"");
        }
    }

    @Transactional
    public void removeSymbol(String email, Long watchlistId, String rawSymbol) {
        Long userId = userIdFor(email);
        Watchlist list = ownedList(userId, watchlistId);
        String symbol = normaliseSymbol(rawSymbol);
        if (watchlistItemRepository.deleteByWatchlistIdAndSymbol(list.getId(), symbol) == 0) {
            throw new WatchListItemNotFoundException(symbol + " is not in \"" + list.getName() + "\"");
        }
    }

    // ---- helpers ----

    private Watchlist ensureDefaultList(Long userId) {
        return watchlistRepository.findByUserIdAndDefaultListTrue(userId)
                .orElseGet(() -> createDefaultList(userId));
    }

    private Watchlist createDefaultList(Long userId) {
        Watchlist list = new Watchlist();
        list.setUserId(userId);
        list.setName(DEFAULT_LIST_NAME);
        list.setDefaultList(true);
        list.setCreatedAt(LocalDateTime.now());
        try {
            return watchlistRepository.saveAndFlush(list);
        } catch (DataIntegrityViolationException e) {
            // A concurrent request created it first; use theirs.
            return watchlistRepository.findByUserIdAndDefaultListTrue(userId).orElseThrow(() -> e);
        }
    }

    private Watchlist ownedList(Long userId, Long watchlistId) {
        return watchlistRepository.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> new WatchlistNotFoundException("Watchlist not found"));
    }

    private void validateSymbol(String symbol) {
        if ("live".equals(marketDataMode)) {
            marketDataService.getQuote(symbol)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown symbol or no live price available: " + symbol));
        } else {
            instrumentService.requireValidSymbol(symbol);
        }
    }

    private Long userIdFor(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"))
                .getId();
    }

    private static String normaliseSymbol(String rawSymbol) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        return rawSymbol.trim().toUpperCase();
    }

    private static String normaliseName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        String name = rawName.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        return name;
    }

    private static DuplicateWatchlistNameException duplicateName(String name) {
        return new DuplicateWatchlistNameException("You already have a watchlist named \"" + name + "\"");
    }
}
