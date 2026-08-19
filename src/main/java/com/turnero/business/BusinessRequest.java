package com.turnero.business;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BusinessRequest(
        @NotBlank
        @Size(max = 160)
        String name,

        @Size(max = 500)
        String shortDescription,

        @Size(max = 40)
        String phone,

        @Email
        @Size(max = 320)
        String contactEmail
) {
}
