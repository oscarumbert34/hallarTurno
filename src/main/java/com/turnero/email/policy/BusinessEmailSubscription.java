package com.turnero.email.policy;

import com.turnero.business.Business;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "business_email_subscriptions")
public class BusinessEmailSubscription {
    @Id @Column(name = "business_id") private UUID businessId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId @JoinColumn(name = "business_id") private Business business;
    @Enumerated(EnumType.STRING) @Column(name = "base_plan", nullable = false) private BasePlan basePlan;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private EmailAddon addon;
    @Enumerated(EnumType.STRING) @Column(name = "pending_addon") private EmailAddon pendingAddon;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SubscriptionStatus status;
    @Column(name = "period_started_at", nullable = false) private Instant periodStartedAt;
    @Column(name = "period_ends_at", nullable = false) private Instant periodEndsAt;
    @Column(name = "trial_started_at") private Instant trialStartedAt;
    @Column(name = "trial_ends_at") private Instant trialEndsAt;
    @Column(name = "explicit_selection", nullable = false) private boolean explicitSelection;
    @Column(name = "confirmation_enabled", nullable = false) private boolean confirmationEnabled;
    @Column(name = "daily_agenda_enabled", nullable = false) private boolean dailyAgendaEnabled;
    @Column(name = "reminder_action_enabled", nullable = false) private boolean reminderActionEnabled;
    @Column(name = "cancellation_enabled", nullable = false) private boolean cancellationEnabled;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected BusinessEmailSubscription() {}

    public static BusinessEmailSubscription trial(Business business, Instant now) {
        BusinessEmailSubscription value = new BusinessEmailSubscription();
        value.business = business;
        value.basePlan = BasePlan.PRO;
        value.addon = EmailAddon.COMPLETE;
        value.status = SubscriptionStatus.TRIAL;
        value.periodStartedAt = now;
        value.periodEndsAt = now.plus(30, ChronoUnit.DAYS);
        value.trialStartedAt = now;
        value.trialEndsAt = value.periodEndsAt;
        value.confirmationEnabled = true;
        value.dailyAgendaEnabled = true;
        value.reminderActionEnabled = true;
        value.cancellationEnabled = true;
        return value;
    }

    public UUID getBusinessId() { return businessId; }
    public Business getBusiness() { return business; }
    public BasePlan getBasePlan() { return basePlan; }
    public EmailAddon getAddon() { return addon; }
    public EmailAddon getPendingAddon() { return pendingAddon; }
    public SubscriptionStatus getStatus() { return status; }
    public Instant getPeriodStartedAt() { return periodStartedAt; }
    public Instant getPeriodEndsAt() { return periodEndsAt; }
    public Instant getTrialStartedAt() { return trialStartedAt; }
    public Instant getTrialEndsAt() { return trialEndsAt; }
    public boolean isExplicitSelection() { return explicitSelection; }
    public boolean isConfirmationEnabled() { return confirmationEnabled; }
    public boolean isDailyAgendaEnabled() { return dailyAgendaEnabled; }
    public boolean isReminderActionEnabled() { return reminderActionEnabled; }
    public boolean isCancellationEnabled() { return cancellationEnabled; }

    public EmailAddon effectiveAddon(Instant now) {
        return status == SubscriptionStatus.TRIAL && trialEndsAt != null && now.isBefore(trialEndsAt)
                ? EmailAddon.COMPLETE : addon;
    }

    public void updatePreferences(boolean confirmation, boolean agenda, boolean reminder, boolean cancellation) {
        confirmationEnabled = confirmation; dailyAgendaEnabled = agenda;
        reminderActionEnabled = reminder; cancellationEnabled = cancellation;
    }

    public void selectAddon(EmailAddon selected, Instant now) {
        explicitSelection = true;
        if (selected.monthlyLimit() >= effectiveAddon(now).monthlyLimit()) {
            addon = selected; pendingAddon = null; status = SubscriptionStatus.ACTIVE;
            periodStartedAt = now; periodEndsAt = now.plus(30, ChronoUnit.DAYS);
        } else {
            pendingAddon = selected;
        }
    }

    public void expireTrial(Instant now) {
        if (status != SubscriptionStatus.TRIAL || trialEndsAt == null || now.isBefore(trialEndsAt)) return;
        if (!explicitSelection) { basePlan = BasePlan.BASIC; addon = EmailAddon.NONE; }
        status = SubscriptionStatus.ACTIVE;
        periodStartedAt = now; periodEndsAt = now.plus(30, ChronoUnit.DAYS);
    }

    public void applyPendingAddon(Instant now) {
        if (pendingAddon != null && !now.isBefore(periodEndsAt)) {
            addon = pendingAddon; pendingAddon = null;
            periodStartedAt = now; periodEndsAt = now.plus(30, ChronoUnit.DAYS);
        }
    }

    public boolean preferenceEnabled(EmailType type) {
        return switch (type) {
            case BOOKING_CONFIRMATION, BOOKING_RESCHEDULE -> confirmationEnabled;
            case BUSINESS_DAILY_AGENDA -> dailyAgendaEnabled;
            case BOOKING_REMINDER_ACTION -> reminderActionEnabled;
            case BUSINESS_CANCELLATION -> cancellationEnabled;
        };
    }
}
