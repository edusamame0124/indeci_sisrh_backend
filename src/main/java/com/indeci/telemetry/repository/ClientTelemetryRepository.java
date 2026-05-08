package com.indeci.telemetry.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.telemetry.entity.ClientTelemetry;

public interface ClientTelemetryRepository extends JpaRepository<ClientTelemetry, Long> {
}
