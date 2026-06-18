package com.oppshan.washa.user;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;

import java.io.Serial;
import java.util.UUID;

@Entity
@Table(name = "google_account",
        schema = "public",
        indexes = {
                @Index(
                        name = "idx_google_account_name",
                        columnList = "name"
                ),
                @Index(
                        name = "idx_google_account_email",
                        columnList = "email"
                ),
        })
public class GoogleAccount extends IdpAccount {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "name")
    private String name;

    @Basic(optional = false)
    @Column(name = "email",
            nullable = false)
    @NotEmpty
    private String email;

    @Column(name = "photo_url",
            length = 2048)
    private String photoUrl;

    @Override
    public GoogleAccount setUuid(UUID uuid) {
        super.setUuid(uuid);
        return this;
    }

    @Override
    public GoogleAccount setProviderId(String providerId) {
        super.setProviderId(providerId);
        return this;
    }

    @Override
    public GoogleAccount setProviderName(String providerName) {
        super.setProviderName(providerName);
        return this;
    }

    @Override
    public GoogleAccount setUserAccount(UserAccount userAccount) {
        super.setUserAccount(userAccount);
        return this;
    }

    public String getName() {
        return name;
    }

    public GoogleAccount setName(String name) {
        this.name = name;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public GoogleAccount setEmail(String email) {
        this.email = email;
        return this;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public GoogleAccount setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
        return this;
    }
}
