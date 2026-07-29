package com.indeci.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.user.entity.RolLegacy;

public interface RolLegacyRepository extends JpaRepository<RolLegacy, Long> {

    Optional<RolLegacy> findFirstByNameIgnoreCase(String name);
}
