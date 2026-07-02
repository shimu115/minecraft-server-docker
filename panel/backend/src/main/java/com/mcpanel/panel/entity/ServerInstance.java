package com.mcpanel.panel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "server_instances", indexes = {
        @Index(name = "idx_instance_name", columnList = "name", unique = true)
})
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getApiKeyId() { return apiKeyId; }
    public void setApiKeyId(Long apiKeyId) { this.apiKeyId = apiKeyId; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public String getServerType() { return serverType; }
    public void setServerType(String serverType) { this.serverType = serverType; }

    public String getMcVersion() { return mcVersion; }
    public void setMcVersion(String mcVersion) { this.mcVersion = mcVersion; }

    public String getRconHost() { return rconHost; }
    public void setRconHost(String rconHost) { this.rconHost = rconHost; }

    public Integer getRconPort() { return rconPort; }
    public void setRconPort(Integer rconPort) { this.rconPort = rconPort; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
