package com.trading.platform.service;

import com.trading.platform.model.mysql.PriceAlert;
import com.trading.platform.model.mysql.PriceAlert.AlertCondition;
import com.trading.platform.model.mysql.PriceAlert.AlertType;
import com.trading.platform.model.mysql.User;
import com.trading.platform.repository.mysql.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private JavaMailSender mailSender;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        UserRepository userRepository = mock(UserRepository.class);
        User user = new User();
        user.setId(1L);
        user.setName("Asha");
        user.setEmail("asha@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service = new NotificationService(mailSender, userRepository);
        ReflectionTestUtils.setField(service, "fromAddress", "alerts@example.com");
    }

    @Test
    void percentageAlertEmailDescribesTheMoveInsteadOfPrintingNull() {
        PriceAlert alert = new PriceAlert();
        alert.setUserId(1L);
        alert.setSymbol("INFY.NS");
        alert.setAlertType(AlertType.PERCENTAGE_CHANGE);
        alert.setCondition(AlertCondition.ABOVE);
        alert.setPercentageThreshold(5.0);
        alert.setBasePrice(new BigDecimal("100.00")); // targetPrice stays null for this type

        String body = sendAndCaptureBody(alert, 106.0);

        assertFalse(body.contains("null"), body);
        assertTrue(body.contains("100.00"), body);   // base price is mentioned
        assertTrue(body.contains("106.00"), body);   // current price is mentioned
    }

    @Test
    void targetPriceAlertEmailShowsTheTarget() {
        PriceAlert alert = new PriceAlert();
        alert.setUserId(1L);
        alert.setSymbol("INFY.NS");
        alert.setAlertType(AlertType.TARGET_PRICE);
        alert.setCondition(AlertCondition.BELOW);
        alert.setTargetPrice(new BigDecimal("1500.00"));

        String body = sendAndCaptureBody(alert, 1490.0);

        assertFalse(body.contains("null"), body);
        assertTrue(body.contains("1500.00"), body);
    }

    @Test
    void noEmailIsSentWhenMailIsNotConfigured() {
        ReflectionTestUtils.setField(service, "fromAddress", "");
        PriceAlert alert = new PriceAlert();
        alert.setUserId(1L);
        alert.setSymbol("INFY.NS");
        alert.setAlertType(AlertType.TARGET_PRICE);
        alert.setCondition(AlertCondition.ABOVE);
        alert.setTargetPrice(new BigDecimal("1500.00"));

        service.sendAlertTriggeredEmail(alert, 1600.0);

        verifyNoInteractions(mailSender);
    }

    private String sendAndCaptureBody(PriceAlert alert, double price) {
        service.sendAlertTriggeredEmail(alert, price);
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue().getText();
    }
}
