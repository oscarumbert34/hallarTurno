package com.turnero.marketplace.dto;

public record SearchCriteria(
        String text,
        String textPattern,
        String locality,
        String localityParameter,
        int page,
        int size,
        int offset,
        int limit,
        int maxSlotsPerService
) {

    public boolean hasText() {
        return this.text != null;
    }

    public boolean hasLocality() {
        return this.locality != null;
    }
}