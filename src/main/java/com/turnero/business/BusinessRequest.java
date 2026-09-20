package com.turnero.business;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BusinessRequest(
        @NotBlank
        @Size(max = 160)
        String name,

        BusinessCategory category,

        @JsonAlias("publicDescription")
        @Size(max = 500)
        String shortDescription,

        @Size(max = 5000)
        String aboutUs,

        @Size(max = 40)
        String whatsapp,

        @Size(max = 255)
        String instagram,

        @Size(max = 40)
        String phone,

        @Email
        @Size(max = 320)
        String contactEmail,

        Boolean depositEnabled
) {
    public BusinessRequest(String name, String shortDescription, String phone, String contactEmail) {
        this(name, null, shortDescription, null, null, null, phone, contactEmail, null);
    }

    public boolean isDepositEnabled() {
        return Boolean.TRUE.equals(depositEnabled);
    }

    public BusinessCategory resolvedCategory() {
        return category == null ? BusinessCategory.OTHERS : category;
    }
}
