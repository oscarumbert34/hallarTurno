package com.turnero.business;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

@Component
class SlugGenerator {

    String uniqueSlug(String value, Predicate<String> exists) {
        String baseSlug = slugify(value);
        String candidate = baseSlug;
        int suffix = 2;
        while (exists.test(candidate)) {
            candidate = baseSlug + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String slugify(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "business" : normalized;
    }
}
