package com.turnero.email.policy;

import jakarta.validation.constraints.NotNull;

public record EmailAddonRequest(@NotNull EmailAddon addon, String reason) {}
