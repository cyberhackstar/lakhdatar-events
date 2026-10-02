package com.neelastack.lakhdatar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "platform_setup_state")
@Getter @Setter
public class PlatformSetupState {
    @Id
    private Short id;

    @Column(name = "initial_admin_completed_at")
    private Instant initialAdminCompletedAt;
}
