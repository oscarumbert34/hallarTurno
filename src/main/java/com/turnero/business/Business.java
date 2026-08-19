package com.turnero.business;

import com.turnero.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "businesses",
        uniqueConstraints = @UniqueConstraint(name = "uk_businesses_slug", columnNames = "slug"),
        indexes = {
                @Index(name = "idx_businesses_owner_id", columnList = "owner_id"),
                @Index(name = "idx_businesses_status", columnList = "status")
        }
)
public class Business {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(length = 40)
    private String phone;

    @Email
    @Column(name = "contact_email", length = 320)
    private String contactEmail;

    @NotBlank
    @Column(nullable = false, length = 180)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BusinessStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Business() {
    }

    private Business(
            User owner,
            String name,
            String shortDescription,
            String phone,
            String contactEmail,
            String slug,
            BusinessStatus status
    ) {
        this.owner = owner;
        this.name = name;
        this.shortDescription = shortDescription;
        this.phone = phone;
        this.contactEmail = contactEmail;
        this.slug = slug;
        this.status = status;
    }

    public static Business create(
            User owner,
            String name,
            String shortDescription,
            String phone,
            String contactEmail,
            String slug,
            BusinessStatus status
    ) {
        return new Business(owner, name, shortDescription, phone, contactEmail, slug, status);
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getPhone() {
        return phone;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getSlug() {
        return slug;
    }

    public BusinessStatus getStatus() {
        return status;
    }

    public User getOwner() {
        return owner;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateDetails(String name, String shortDescription, String phone, String contactEmail) {
        this.name = name;
        this.shortDescription = shortDescription;
        this.phone = phone;
        this.contactEmail = contactEmail;
    }
}
