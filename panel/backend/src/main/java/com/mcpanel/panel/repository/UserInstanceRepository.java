package com.mcpanel.panel.repository;

import com.mcpanel.panel.entity.UserInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserInstanceRepository extends JpaRepository<UserInstance, Long> {

    Optional<UserInstance> findByUserIdAndInstanceId(Long userId, Long instanceId);

    List<UserInstance> findByUserId(Long userId);

    List<UserInstance> findByInstanceId(Long instanceId);

    void deleteByUserIdAndInstanceId(Long userId, Long instanceId);

    boolean existsByUserIdAndInstanceId(Long userId, Long instanceId);
}
