package com.trading.platform.service;

import com.trading.platform.dto.response.WatchlistItemResponse;
import com.trading.platform.dto.response.WatchlistResponse;
import com.trading.platform.exception.DuplicateWatchListItemException;
import com.trading.platform.exception.DuplicateWatchlistNameException;
import com.trading.platform.exception.UserNotFoundException;
import com.trading.platform.exception.WatchListItemNotFoundException;
import com.trading.platform.exception.WatchlistNotFoundException;
import com.trading.platform.model.mysql.User;
import com.trading.platform.model.mysql.Watchlist;
import com.trading.platform.model.mysql.WatchlistItem;
import com.trading.platform.repository.mysql.UserRepository;
import com.trading.platform.repository.mysql.WatchlistItemRepository;
import com.trading.platform.repository.mysql.WatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatchlistManagementServiceTest {

    private static final String EMAIL = "asha@example.com";
    private static final Long USER_ID = 7L;
    private static final Long LIST_ID = 11L;

    private WatchlistRepository lists;
    private WatchlistItemRepository items;
    private UserRepository users;
    private InstrumentService instruments;
    private MarketDataService market;
    private WatchlistManagementService service;

    @BeforeEach
    void setUp() {
        lists = mock(WatchlistRepository.class);
        items = mock(WatchlistItemRepository.class);
        users = mock(UserRepository.class);
        instruments = mock(InstrumentService.class);
        market = mock(MarketDataService.class);
        service = new WatchlistManagementService(lists, items, users, instruments, market);
        ReflectionTestUtils.setField(service, "marketDataMode", "simulated");

        User user = new User();
        user.setId(USER_ID);
        user.setEmail(EMAIL);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        // By default the user already has a default list and owns one more list (id 11, "Banking").
        when(lists.findByUserIdAndDefaultListTrue(USER_ID)).thenReturn(Optional.of(list(1L, "Default", true)));
        when(lists.findByIdAndUserId(LIST_ID, USER_ID)).thenReturn(Optional.of(list(LIST_ID, "Banking", false)));

        when(lists.saveAndFlush(any(Watchlist.class))).thenAnswer(inv -> inv.getArgument(0));
        when(items.saveAndFlush(any(WatchlistItem.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------------------------------------------------------------- getWatchlists

    @Test
    void getWatchlistsCreatesTheDefaultListOnFirstUse() {
        when(lists.findByUserIdAndDefaultListTrue(USER_ID)).thenReturn(Optional.empty());
        when(lists.findByUserIdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(list(1L, "Default", true)));

        List<WatchlistResponse> result = service.getWatchlists(EMAIL);

        ArgumentCaptor<Watchlist> captor = ArgumentCaptor.forClass(Watchlist.class);
        verify(lists).saveAndFlush(captor.capture());
        assertEquals("Default", captor.getValue().getName());
        assertTrue(captor.getValue().isDefaultList());
        assertEquals(USER_ID, captor.getValue().getUserId());
        assertEquals(1, result.size());
    }

    @Test
    void getWatchlistsDoesNotCreateAnotherDefaultListWhenOneExists() {
        when(lists.findByUserIdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(list(1L, "Default", true)));

        service.getWatchlists(EMAIL);

        verify(lists, never()).saveAndFlush(any(Watchlist.class));
    }

    @Test
    void getWatchlistsUsesTheListAnotherRequestCreatedFirst() {
        when(lists.findByUserIdAndDefaultListTrue(USER_ID))
                .thenReturn(Optional.empty(), Optional.of(list(1L, "Default", true)));
        when(lists.saveAndFlush(any(Watchlist.class))).thenThrow(new DataIntegrityViolationException("unique"));
        when(lists.findByUserIdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(list(1L, "Default", true)));

        List<WatchlistResponse> result = service.getWatchlists(EMAIL);

        assertEquals(1, result.size());
    }

    @Test
    void getWatchlistsPutsEachItemUnderItsOwnList() {
        when(lists.findByUserIdOrderByCreatedAtAsc(USER_ID))
                .thenReturn(List.of(list(1L, "Default", true), list(2L, "Banking", false)));
        when(items.findByWatchlistIdInOrderByAddedAtDesc(List.of(1L, 2L)))
                .thenReturn(List.of(item(1L, "INFY.NS"), item(2L, "TCS.NS"), item(1L, "SBIN.NS")));

        List<WatchlistResponse> result = service.getWatchlists(EMAIL);

        assertEquals(List.of("INFY.NS", "SBIN.NS"), symbols(result.get(0)));
        assertEquals(List.of("TCS.NS"), symbols(result.get(1)));
    }

    @Test
    void anUnknownUserIsRejected() {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.getWatchlists("ghost@example.com"));
    }

    // ---------------------------------------------------------------- createWatchlist

    @Test
    void createWatchlistSavesATrimmedNonDefaultList() {
        WatchlistResponse response = service.createWatchlist(EMAIL, "  Banking  ");

        ArgumentCaptor<Watchlist> captor = ArgumentCaptor.forClass(Watchlist.class);
        verify(lists).saveAndFlush(captor.capture());
        assertEquals("Banking", captor.getValue().getName());
        assertFalse(captor.getValue().isDefaultList());
        assertEquals(USER_ID, captor.getValue().getUserId());
        assertNotNull(captor.getValue().getCreatedAt());
        assertEquals("Banking", response.name());
        assertTrue(response.items().isEmpty());
    }

    @Test
    void createWatchlistRejectsADuplicateName() {
        when(lists.existsByUserIdAndNameIgnoreCase(USER_ID, "Banking")).thenReturn(true);

        assertThrows(DuplicateWatchlistNameException.class, () -> service.createWatchlist(EMAIL, "Banking"));
        verify(lists, never()).saveAndFlush(any(Watchlist.class));
    }

    @Test
    void createWatchlistRejectsTheEleventhList() {
        when(lists.countByUserId(USER_ID)).thenReturn((long) WatchlistManagementService.MAX_LISTS_PER_USER);

        assertThrows(IllegalArgumentException.class, () -> service.createWatchlist(EMAIL, "One more"));
        verify(lists, never()).saveAndFlush(any(Watchlist.class));
    }

    @Test
    void createWatchlistRejectsMissingBlankAndTooLongNames() {
        assertThrows(IllegalArgumentException.class, () -> service.createWatchlist(EMAIL, null));
        assertThrows(IllegalArgumentException.class, () -> service.createWatchlist(EMAIL, ""));
        assertThrows(IllegalArgumentException.class, () -> service.createWatchlist(EMAIL, "   "));
        assertThrows(IllegalArgumentException.class,
                () -> service.createWatchlist(EMAIL, "x".repeat(WatchlistManagementService.MAX_NAME_LENGTH + 1)));
        verify(lists, never()).saveAndFlush(any(Watchlist.class));
    }

    @Test
    void createWatchlistTurnsAConstraintViolationIntoADuplicateNameError() {
        when(lists.saveAndFlush(any(Watchlist.class))).thenThrow(new DataIntegrityViolationException("unique"));

        assertThrows(DuplicateWatchlistNameException.class, () -> service.createWatchlist(EMAIL, "Banking"));
    }

    // ---------------------------------------------------------------- renameWatchlist

    @Test
    void renameChangesTheName() {
        WatchlistResponse response = service.renameWatchlist(EMAIL, LIST_ID, " Banks ");

        ArgumentCaptor<Watchlist> captor = ArgumentCaptor.forClass(Watchlist.class);
        verify(lists).saveAndFlush(captor.capture());
        assertEquals("Banks", captor.getValue().getName());
        assertEquals("Banks", response.name());
    }

    @Test
    void renameRejectsANameAnotherListAlreadyHas() {
        when(lists.existsByUserIdAndNameIgnoreCase(USER_ID, "Banks")).thenReturn(true);

        assertThrows(DuplicateWatchlistNameException.class, () -> service.renameWatchlist(EMAIL, LIST_ID, "Banks"));
        verify(lists, never()).saveAndFlush(any(Watchlist.class));
    }

    @Test
    void renameAllowsChangingOnlyTheLetterCaseOfTheSameName() {
        // The name check would find the list itself, so the service must not treat that as a clash.
        when(lists.existsByUserIdAndNameIgnoreCase(USER_ID, "BANKING")).thenReturn(true);

        WatchlistResponse response = service.renameWatchlist(EMAIL, LIST_ID, "BANKING");

        assertEquals("BANKING", response.name());
    }

    @Test
    void renameOfAListThatIsNotYoursLooksLikeItDoesNotExist() {
        assertThrows(WatchlistNotFoundException.class, () -> service.renameWatchlist(EMAIL, 99L, "Anything"));
        verify(lists, never()).saveAndFlush(any(Watchlist.class));
    }

    // ---------------------------------------------------------------- deleteWatchlist

    @Test
    void deleteRemovesTheItemsAndThenTheList() {
        Watchlist banking = list(LIST_ID, "Banking", false);
        when(lists.findByIdAndUserId(LIST_ID, USER_ID)).thenReturn(Optional.of(banking));

        service.deleteWatchlist(EMAIL, LIST_ID);

        InOrder order = inOrder(items, lists);
        order.verify(items).deleteByWatchlistId(LIST_ID);
        order.verify(lists).delete(banking);
    }

    @Test
    void theDefaultListCannotBeDeleted() {
        when(lists.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(list(1L, "Default", true)));

        assertThrows(IllegalArgumentException.class, () -> service.deleteWatchlist(EMAIL, 1L));
        verify(items, never()).deleteByWatchlistId(anyLong());
        verify(lists, never()).delete(any(Watchlist.class));
    }

    @Test
    void deleteOfAListThatIsNotYoursChangesNothing() {
        assertThrows(WatchlistNotFoundException.class, () -> service.deleteWatchlist(EMAIL, 99L));
        verify(items, never()).deleteByWatchlistId(anyLong());
        verify(lists, never()).delete(any(Watchlist.class));
    }

    // ---------------------------------------------------------------- addSymbol

    @Test
    void addSymbolSavesTheNormalisedSymbolInTheRightList() {
        WatchlistItemResponse response = service.addSymbol(EMAIL, LIST_ID, "  sbin.ns ");

        ArgumentCaptor<WatchlistItem> captor = ArgumentCaptor.forClass(WatchlistItem.class);
        verify(items).saveAndFlush(captor.capture());
        assertEquals("SBIN.NS", captor.getValue().getSymbol());
        assertEquals(LIST_ID, captor.getValue().getWatchlistId());
        assertNotNull(captor.getValue().getAddedAt());
        assertEquals("SBIN.NS", response.symbol());
    }

    @Test
    void addSymbolRejectsADuplicateInTheSameListBeforeAnyNetworkCall() {
        ReflectionTestUtils.setField(service, "marketDataMode", "live");
        when(items.existsByWatchlistIdAndSymbol(LIST_ID, "SBIN.NS")).thenReturn(true);

        assertThrows(DuplicateWatchListItemException.class, () -> service.addSymbol(EMAIL, LIST_ID, "SBIN.NS"));
        verify(market, never()).getQuote(anyString());
        verify(items, never()).saveAndFlush(any(WatchlistItem.class));
    }

    @Test
    void addSymbolRejectsAFullList() {
        when(items.countByWatchlistId(LIST_ID)).thenReturn((long) WatchlistManagementService.MAX_SYMBOLS_PER_LIST);

        assertThrows(IllegalArgumentException.class, () -> service.addSymbol(EMAIL, LIST_ID, "SBIN.NS"));
        verify(items, never()).saveAndFlush(any(WatchlistItem.class));
    }

    @Test
    void addSymbolRejectsAFiftyFirstDistinctSymbol() {
        when(items.findDistinctSymbolsByUserId(USER_ID))
                .thenReturn(symbolsNamed(WatchlistManagementService.MAX_DISTINCT_SYMBOLS_PER_USER));

        assertThrows(IllegalArgumentException.class, () -> service.addSymbol(EMAIL, LIST_ID, "SBIN.NS"));
        verify(items, never()).saveAndFlush(any(WatchlistItem.class));
    }

    @Test
    void addSymbolAllowsASymbolTheUserAlreadyFollowsEvenAtTheLimit() {
        List<String> atTheLimit = Stream.concat(
                symbolsNamed(WatchlistManagementService.MAX_DISTINCT_SYMBOLS_PER_USER - 1).stream(),
                Stream.of("SBIN.NS")).toList();
        when(items.findDistinctSymbolsByUserId(USER_ID)).thenReturn(atTheLimit);

        WatchlistItemResponse response = service.addSymbol(EMAIL, LIST_ID, "SBIN.NS");

        assertEquals("SBIN.NS", response.symbol());
    }

    @Test
    void inSimulatedModeASymbolIsCheckedAgainstTheInstrumentsTable() {
        service.addSymbol(EMAIL, LIST_ID, "SBIN.NS");

        verify(instruments).requireValidSymbol("SBIN.NS");
        verify(market, never()).getQuote(anyString());
    }

    @Test
    void inSimulatedModeAnUnknownSymbolIsRejected() {
        when(instruments.requireValidSymbol("FAKE123"))
                .thenThrow(new IllegalArgumentException("Unknown or inactive symbol: FAKE123"));

        assertThrows(IllegalArgumentException.class, () -> service.addSymbol(EMAIL, LIST_ID, "FAKE123"));
        verify(items, never()).saveAndFlush(any(WatchlistItem.class));
    }

    @Test
    void inLiveModeASymbolWithNoQuoteIsRejected() {
        ReflectionTestUtils.setField(service, "marketDataMode", "live");
        when(market.getQuote("SBIN.NS")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.addSymbol(EMAIL, LIST_ID, "SBIN.NS"));
        verify(instruments, never()).requireValidSymbol(anyString());
        verify(items, never()).saveAndFlush(any(WatchlistItem.class));
    }

    @Test
    void inLiveModeASymbolWithAQuoteIsSaved() {
        ReflectionTestUtils.setField(service, "marketDataMode", "live");
        when(market.getQuote("SBIN.NS")).thenReturn(Optional.of(BigDecimal.TEN));

        service.addSymbol(EMAIL, LIST_ID, "SBIN.NS");

        verify(items).saveAndFlush(any(WatchlistItem.class));
    }

    @Test
    void addSymbolTurnsAConstraintViolationIntoADuplicateError() {
        when(items.saveAndFlush(any(WatchlistItem.class))).thenThrow(new DataIntegrityViolationException("unique"));

        assertThrows(DuplicateWatchListItemException.class, () -> service.addSymbol(EMAIL, LIST_ID, "SBIN.NS"));
    }

    @Test
    void addSymbolRejectsMissingAndBlankSymbols() {
        assertThrows(IllegalArgumentException.class, () -> service.addSymbol(EMAIL, LIST_ID, null));
        assertThrows(IllegalArgumentException.class, () -> service.addSymbol(EMAIL, LIST_ID, ""));
        assertThrows(IllegalArgumentException.class, () -> service.addSymbol(EMAIL, LIST_ID, "   "));
        verify(items, never()).saveAndFlush(any(WatchlistItem.class));
    }

    @Test
    void addSymbolToAListThatIsNotYoursLooksLikeItDoesNotExist() {
        assertThrows(WatchlistNotFoundException.class, () -> service.addSymbol(EMAIL, 99L, "SBIN.NS"));
        verify(items, never()).saveAndFlush(any(WatchlistItem.class));
    }

    // ---------------------------------------------------------------- removeSymbol

    @Test
    void removeSymbolUsesTheNormalisedSymbol() {
        when(items.deleteByWatchlistIdAndSymbol(LIST_ID, "SBIN.NS")).thenReturn(1L);

        service.removeSymbol(EMAIL, LIST_ID, " sbin.ns ");

        verify(items).deleteByWatchlistIdAndSymbol(LIST_ID, "SBIN.NS");
    }

    @Test
    void removeSymbolThatIsNotInTheListIsReportedAsNotFound() {
        assertThrows(WatchListItemNotFoundException.class, () -> service.removeSymbol(EMAIL, LIST_ID, "SBIN.NS"));
    }

    @Test
    void removeSymbolFromAListThatIsNotYoursLooksLikeItDoesNotExist() {
        assertThrows(WatchlistNotFoundException.class, () -> service.removeSymbol(EMAIL, 99L, "SBIN.NS"));
        verify(items, never()).deleteByWatchlistIdAndSymbol(anyLong(), anyString());
    }

    // ---------------------------------------------------------------- helpers

    private static Watchlist list(Long id, String name, boolean isDefault) {
        Watchlist list = new Watchlist();
        list.setId(id);
        list.setUserId(USER_ID);
        list.setName(name);
        list.setDefaultList(isDefault);
        list.setCreatedAt(LocalDateTime.now());
        return list;
    }

    private static WatchlistItem item(Long watchlistId, String symbol) {
        WatchlistItem item = new WatchlistItem();
        item.setWatchlistId(watchlistId);
        item.setSymbol(symbol);
        item.setAddedAt(LocalDateTime.now());
        return item;
    }

    private static List<String> symbols(WatchlistResponse response) {
        return response.items().stream().map(WatchlistItemResponse::symbol).toList();
    }

    private static List<String> symbolsNamed(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "SYM" + i + ".NS").toList();
    }
}
