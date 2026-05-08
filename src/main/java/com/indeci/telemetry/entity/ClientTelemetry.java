package com.indeci.telemetry.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "CLIENT_TELEMETRY", schema = "GESTIONRRHH")
@Data
public class ClientTelemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CATEGORY", nullable = false, length = 120)
    private String category;

    @Lob
    @Column(name = "PAYLOAD_JSON")
    private String payloadJson;

    @Column(name = "IP", length = 128)
    private String ip;

    @Column(name = "USER_AGENT", length = 512)
    private String userAgent;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}
