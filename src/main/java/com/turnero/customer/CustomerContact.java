package com.turnero.customer;

import com.turnero.business.Business;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "customer_contacts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_customer_contacts_business_phone",
                columnNames = {"business_id", "normalized_phone"}
        ),
        indexes = {
                @Index(name = "idx_customer_contacts_business_id", columnList = "business_id")
        }
)
public class CustomerContact {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String name;

    @NotBlank
    @Column(nullable = false, length = 40)
    private String phone;

    @NotBlank
    @Column(name = "normalized_phone", nullable = false, length = 40)
    private String normalizedPhone;

    @Email
    @Column(length = 320)
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerContact() {
    }

    private CustomerContact(Business business, String name, String phone, String email) {
        this.business = business;
        this.name = name.trim();
        this.phone = phone.trim();
        this.normalizedPhone = normalizePhone(phone);
        this.email = blankToNull(email);
    }

    public static CustomerContact create(Business business, String name, String phone, String email) {
        return new CustomerContact(business, name, phone, email);
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        normalize();
    }

    @PreUpdate
    void preUpdate() {
        normalize();
    }

    public UUID getId() {
        return id;
    }

    public Business getBusiness() {
        return business;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getNormalizedPhone() {
        return normalizedPhone;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateFromBooking(String name, String phone, String email) {
        this.name = name.trim();
        this.phone = phone.trim();
        this.normalizedPhone = normalizePhone(phone);
        String normalizedEmail = blankToNull(email);
        if (normalizedEmail != null || this.email == null) {
            this.email = normalizedEmail;
        }
    }

    public static String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }
        return phone.replaceAll("[^0-9]", "");
    }

    private void normalize() {
        name = name.trim();
        phone = phone.trim();
        normalizedPhone = normalizePhone(phone);
        email = blankToNull(email);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
