package com.turnero.marketplace;

import com.turnero.marketplace.dto.SearchCriteria;

import java.text.Normalizer;

class PublicAvailabilityCriteriaNormalizer {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_MAX_SLOTS_PER_SERVICE = 10;
    private static final int MAX_SLOTS_PER_SERVICE = 50;

    SearchCriteria normalize(
            final String text,
            final String service,
            final int page,
            final int size,
            final int offset,
            final Integer limit,
            final int maxSlotsPerService,
            final String locality
    ) {
        final String normalizedText = this.normalizeSearchText(service == null || service.isBlank() ? text : service);
        final String normalizedLocality = this.normalizeText(locality);
        return new SearchCriteria(
                normalizedText,
                normalizedText == null ? "" : "%" + normalizedText + "%",
                normalizedLocality,
                normalizedLocality == null ? "" : normalizedLocality,
                Math.max(page, 0),
                this.normalizePositive(size, DEFAULT_SIZE, MAX_SIZE),
                Math.max(offset, 0),
                this.normalizeLimit(limit == null ? DEFAULT_LIMIT : limit),
                this.normalizePositive(maxSlotsPerService, DEFAULT_MAX_SLOTS_PER_SERVICE, MAX_SLOTS_PER_SERVICE)
        );
    }

    int normalizeLimit(final int limit) {
        return this.normalizePositive(limit, DEFAULT_LIMIT, MAX_LIMIT);
    }

    private String normalizeText(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private String normalizeSearchText(final String value) {
        final String normalized = this.normalizeText(value);
        if (normalized == null) {
            return null;
        }
        return Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private int normalizePositive(final int requested, final int defaultValue, final int maxValue) {
        if (requested <= 0) {
            return defaultValue;
        }
        return Math.min(requested, maxValue);
    }
}
