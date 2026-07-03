package com.mcpanel.panel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_instances", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "instance_id"})
})
@Data
public class UserInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
