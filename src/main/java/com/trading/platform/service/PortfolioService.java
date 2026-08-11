package com.trading.platform.service;

import com.trading.platform.model.mysql.Holding;
import com.trading.platform.repository.mysql.HoldingRepository;
import com.trading.platform.repository.mysql.UserRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PortfolioService {

    private final HoldingRepository holdingRepository;
    private final UserRepository userRepository;

    public PortfolioService(HoldingRepository holdingRepository, UserRepository userRepository) {
        this.holdingRepository = holdingRepository;
        this.userRepository = userRepository;
    }

    public List<Holding> getUserPortfolio(String email) {
        Long userId = getUserIdFromEmail(email);
        return holdingRepository.findByUserId(userId);
    }

    public Holding addHolding(String email, Holding holding) {
        Long userId = getUserIdFromEmail(email);
        holding.setUserId(userId);
        return holdingRepository.save(holding);
    }

    public void deleteHolding(String email, Long holdingId) {
        Long userId = getUserIdFromEmail(email);

        Holding holding = holdingRepository.findById(holdingId)
                .orElseThrow(() -> new RuntimeException("Holding not found"));

        if (!holding.getUserId().equals(userId)) {
            throw new RuntimeException("You do not have permission to delete this holding");
        }

        holdingRepository.deleteById(holdingId);
    }

    private Long getUserIdFromEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
    }
}