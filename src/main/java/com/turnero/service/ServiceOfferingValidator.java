package com.turnero.service;

import com.turnero.common.ApiException;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class ServiceOfferingValidator {

    String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "ARS";
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Currency must be an ISO-4217 code");
        }
        return normalized;
    }

    BigDecimal normalizePrice(BigDecimal price) {
        if (price.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Price must be zero or positive");
        }
        return price.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }
}
