package com.mcpanel.panel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "server_instances", indexes = {
        @Index(name = "idx_instance_name", columnList = "name", unique = true)
})
@Data
public class ServerInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String name;

    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private Integer port = 25560;

    @Column(name = "server_type", nullable = false, length = 32)
    private String serverType;

    @Column(name = "mc_version", nullable = false, length = 16)
    private String mcVersion;

    @Column(name = "rcon_host", length = 255)
    private String rconHost;

    @Column(name = "rcon_port")
    private Integer rconPort;

    @Column(length = 16)
    private String status = "unknown";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
