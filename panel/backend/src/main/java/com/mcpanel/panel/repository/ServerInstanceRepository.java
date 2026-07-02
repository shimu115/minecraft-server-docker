package com.mcpanel.panel.repository;

import com.mcpanel.panel.entity.ServerInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServerInstanceRepository extends JpaRepository<ServerInstance, Long> {

    boolean existsByName(String name);

    Optional<ServerInstance> findByApiKeyId(Long apiKeyId);

    List<ServerInstance> findAllByOrderByCreatedAtDesc();
}
