package com.trading.platform.service;

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
}