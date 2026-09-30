package com.turnero.email.policy;

public enum EmailAddon {
    NONE(0), ESSENTIAL(400), COMPLETE(1300);

    private final int monthlyLimit;

    EmailAddon(int monthlyLimit) { this.monthlyLimit = monthlyLimit; }
    public int monthlyLimit() { return monthlyLimit; }
}
