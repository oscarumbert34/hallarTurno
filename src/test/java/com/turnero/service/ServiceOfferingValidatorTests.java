package com.turnero.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.turnero.common.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ServiceOfferingValidatorTests {

    private final ServiceOfferingValidator validator = new ServiceOfferingValidator();

    @Test
    void defaultsCurrencyToArsAndNormalizesUppercase() {
        assertThat(validator.normalizeCurrency(null)).isEqualTo("ARS");
        assertThat(validator.normalizeCurrency(" usd ")).isEqualTo("USD");
    }

    @Test
    void rejectsInvalidCurrency() {
        assertThatThrownBy(() -> validator.normalizeCurrency("AR$"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Currency must be an ISO-4217 code");
    }

    @Test
    void normalizesPriceToTwoDecimals() {
        assertThat(validator.normalizePrice(new BigDecimal("1500")))
                .isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void rejectsNegativePrice() {
        assertThatThrownBy(() -> validator.normalizePrice(new BigDecimal("-1.00")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Price must be zero or positive");
    }
}
