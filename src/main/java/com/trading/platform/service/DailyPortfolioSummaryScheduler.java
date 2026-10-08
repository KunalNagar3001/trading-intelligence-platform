package com.trading.platform.service;

import com.trading.platform.dto.response.PortfolioSummary;
import com.trading.platform.model.mysql.User;
import com.trading.platform.repository.mysql.HoldingRepository;
import com.trading.platform.repository.mysql.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SRS 3.3 — "Daily portfolio summary delivered each morning."
 * <p>
 * Runs once a day, well after NSE's 9:15 AM open so the day's opening prices
 * are already available, and emails every user who holds at least one
 * position their current P&L via {@link PortfolioService#getPortfolioSummary}
 * and {@link NotificationService#sendPortfolioSummaryEmail}. Users with an
 * empty portfolio are skipped rather than sent an all-zeros email.
 * <p>
 * One user's failure (bad email, a single symbol's quote failing) must not
 * stop the rest of the batch — both getPortfolioSummary (falls back to
 * buyPrice per-holding) and sendPortfolioSummaryEmail (never throws) are
 * already defensive, so this loop only needs to guard itself the same way.
 */
@Component
public class DailyPortfolioSummaryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyPortfolioSummaryScheduler.class);

    private final HoldingRepository holdingRepository;
    private final UserRepository userRepository;
    private final PortfolioService portfolioService;
    private final NotificationService notificationService;

    public DailyPortfolioSummaryScheduler(HoldingRepository holdingRepository,
                                          UserRepository userRepository,
                                          PortfolioService portfolioService,
                                          NotificationService notificationService) {
        this.holdingRepository = holdingRepository;
        this.userRepository = userRepository;
        this.portfolioService = portfolioService;
        this.notificationService = notificationService;
    }

    // 8:00 AM IST daily. Cron fields are second minute hour day month weekday.
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Kolkata")
    public void sendDailySummaries() {
        List<Long> userIds = holdingRepository.findDistinctUserIds();
        if (userIds.isEmpty()) {
            log.info("Daily portfolio summary: no users with holdings, nothing to send");
            return;
        }

        log.info("Daily portfolio summary: sending to {} user(s)", userIds.size());
        int sent = 0;

        for (Long userId : userIds) {
            try {
                User user = userRepository.findById(userId).orElse(null);
                if (user == null) {
                    log.warn("Skipping daily summary — user {} not found (holding orphaned?)", userId);
                    continue;
                }

                PortfolioSummary summary = portfolioService.getPortfolioSummary(user.getEmail());
                notificationService.sendPortfolioSummaryEmail(user, summary);
                sent++;
            } catch (Exception e) {
                // A single user's data problem (missing symbol, bad quote batch, etc.)
                // must not stop everyone else's morning email from going out.
                log.error("Failed to build/send daily portfolio summary for user {}", userId, e);
            }
        }

        log.info("Daily portfolio summary: completed {} of {} user(s)", sent, userIds.size());
    }
}