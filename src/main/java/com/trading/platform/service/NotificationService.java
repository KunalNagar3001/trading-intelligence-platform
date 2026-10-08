package com.trading.platform.service;

import com.trading.platform.dto.response.HoldingPnl;
import com.trading.platform.dto.response.PortfolioSummary;
import com.trading.platform.model.mongo.NewsArticle;
import com.trading.platform.model.mysql.PriceAlert;
import com.trading.platform.model.mysql.User;
import com.trading.platform.repository.mysql.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    public NotificationService(JavaMailSender mailSender, UserRepository userRepository) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
    }

    /**
     * Sends an email to the alert's owner informing them the alert has triggered.
     * Never throws — a mail failure (bad SMTP config, network hiccup, etc.) must not
     * break Kafka message processing or prevent the alert from being deactivated.
     */
    public void sendAlertTriggeredEmail(PriceAlert alert, double currentPrice) {
        userRepository.findById(alert.getUserId()).ifPresentOrElse(
                user -> sendEmail(user, alert, currentPrice),
                () -> log.warn("Skipping alert email — user {} not found for alert {}",
                        alert.getUserId(), alert.getId())
        );
    }

    // NEW — sends a news alert email to a user for a symbol they hold.
    // Same never-throws contract as the price alert method, for the same reason:
    // this runs inside NewsProcessingConsumer's Kafka listener loop.
    public void sendNewsAlertEmail(User user, NewsArticle article, String symbol) {
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("spring.mail.username is not configured — skipping news email to {}", user.getEmail());
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(user.getEmail());
            message.setSubject("News Alert: " + symbol);
            message.setText(buildNewsBody(user, article, symbol));
            mailSender.send(message);
            log.info("News alert email sent to {} for {}", user.getEmail(), symbol);
        } catch (MailException e) {
            log.error("Failed to send news alert email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    // NEW — sends the daily morning portfolio summary. Same never-throws contract:
    // this runs inside a @Scheduled loop over every user with holdings, and one
    // user's bad email address or a transient SMTP error must not stop the rest
    // of the batch from being sent.
    public void sendPortfolioSummaryEmail(User user, PortfolioSummary summary) {
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("spring.mail.username is not configured — skipping portfolio summary email to {}", user.getEmail());
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(user.getEmail());
            message.setSubject("Your Daily Portfolio Summary");
            message.setText(buildSummaryBody(user, summary));
            mailSender.send(message);
            log.info("Portfolio summary email sent to {}", user.getEmail());
        } catch (MailException e) {
            log.error("Failed to send portfolio summary email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private void sendEmail(User user, PriceAlert alert, double currentPrice) {
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("spring.mail.username is not configured — skipping alert email to {}", user.getEmail());
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(user.getEmail());
            message.setSubject("Price Alert Triggered: " + alert.getSymbol());
            message.setText(buildBody(user, alert, currentPrice));
            mailSender.send(message);
            log.info("Alert email sent to {} for {} ({})", user.getEmail(), alert.getSymbol(), alert.getCondition());
        } catch (MailException e) {
            log.error("Failed to send alert email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private String buildBody(User user, PriceAlert alert, double currentPrice) {
        String direction = alert.getCondition() == PriceAlert.AlertCondition.ABOVE
                ? "risen above"
                : "fallen below";

        return String.format(
                "Hi %s,%n%n" +
                        "Your price alert for %s has been triggered.%n%n" +
                        "Condition: price %s ₹%s%n" +
                        "Current price: ₹%.2f%n%n" +
                        "This alert has now been deactivated. You can set a new one anytime from your dashboard.%n%n" +
                        "— Trading Intelligence Platform",
                user.getName(), alert.getSymbol(), direction, alert.getTargetPrice(), currentPrice
        );
    }

    private String buildNewsBody(User user, NewsArticle article, String symbol) {
        return String.format(
                "Hi %s,%n%n" +
                        "Breaking news on %s, a stock you hold:%n%n" +
                        "%s%n%n" +
                        "Source: %s%n" +
                        "Read more: %s%n%n" +
                        "— Trading Intelligence Platform",
                user.getName(), symbol, article.getHeadline(), article.getSource(), article.getUrl()
        );
    }

    private String buildSummaryBody(User user, PortfolioSummary summary) {
        StringBuilder holdingLines = new StringBuilder();
        for (HoldingPnl h : summary.holdings()) {
            String staleTag = h.priceStale() ? "  (price unavailable — showing buy price)" : "";
            holdingLines.append(String.format(
                    "%-14s qty %-5d ₹%-10s -> ₹%-10s  P&L ₹%s (%s%%)%s%n",
                    h.symbol(), h.quantity(), h.buyPrice(), h.currentPrice(),
                    h.profitLoss(), h.profitLossPercent(), staleTag
            ));
        }

        StringBuilder allocationLines = new StringBuilder();
        summary.assetAllocationPercent().forEach((assetType, percent) ->
                allocationLines.append(String.format("%-10s %s%%%n", assetType, percent)));

        return String.format(
                "Good morning %s,%n%n" +
                        "Here's where your portfolio stands today:%n%n" +
                        "%s%n" +
                        "Total invested:     ₹%s%n" +
                        "Current value:      ₹%s%n" +
                        "Overall P&L:         ₹%s (%s%%)%n%n" +
                        "Asset allocation:%n%s%n" +
                        "— Trading Intelligence Platform",
                user.getName(), holdingLines, summary.totalInvested(), summary.totalCurrentValue(),
                summary.totalProfitLoss(), summary.totalProfitLossPercent(), allocationLines
        );
    }
}