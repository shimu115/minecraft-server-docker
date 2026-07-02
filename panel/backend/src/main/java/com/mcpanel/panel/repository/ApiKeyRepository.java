package com.mcpanel.panel.repository;

import com.mcpanel.panel.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    boolean existsByName(String name);

    List<ApiKey> findByStatus(String status);
}
