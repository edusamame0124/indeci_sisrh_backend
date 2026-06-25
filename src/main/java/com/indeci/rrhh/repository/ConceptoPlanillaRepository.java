package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.ConceptoPlanilla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConceptoPlanillaRepository
        extends JpaRepository<ConceptoPlanilla, Long> {

    List<ConceptoPlanilla> findByActivo(Integer activo);

    /**
     * SPEC_CONCEPTOS_PLANILLA P1 — catálogo de gestión: todos los conceptos cuyo
     * {@code ESTADO} no sea ANULADO (incluye BORRADOR/EN_REVISION, que tienen
     * {@code ACTIVO=0} y por tanto NO aparecen en {@link #findByActivo}). El motor
     * sigue usando {@code findByActivo}/búsquedas por código; este método es solo
     * para la pantalla del catálogo.
     */
    List<ConceptoPlanilla> findByEstadoNot(String estado);

    /**
     * SPEC_CONCEPTOS_PLANILLA P3 — lookup defensivo del concepto vigente por CÓDIGO.
     *
     * <p>El motor lo usa con códigos INTERNOS (IR4TA_CAS, SUBSIDIO_DIF_CAS) y espera
     * {@code Optional<uno>}. Con el versionado podría existir transitoriamente más de
     * una fila ACTIVO para el mismo código (durante un supersede). Para no caer en
     * {@code NonUniqueResultException}, esta query nativa ordena por última vigencia
     * y limita a 1 fila (mismo patrón que {@link #findByCodigoMefAndActivo}). El
     * invariante "1 ACTIVO por código" lo mantiene además {@code activar()}.</p>
     */
    @Query(value = """
            SELECT *
              FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
             WHERE CODIGO = :codigo
               AND ACTIVO = :activo
             ORDER BY FECHA_VIG_INI DESC NULLS LAST,
                      VERSION DESC NULLS LAST,
                      ID DESC
             FETCH FIRST 1 ROWS ONLY
            """, nativeQuery = true)
    Optional<ConceptoPlanilla>
    findByCodigoAndActivo(
            @Param("codigo") String codigo,
            @Param("activo") Integer activo);

    /** SPEC_CONCEPTOS_PLANILLA P3 — versiones del mismo CÓDIGO para el historial. */
    List<ConceptoPlanilla> findByCodigoOrderByVersionDesc(String codigo);

    /** Spec 010 — búsqueda por código MEF oficial (LEY-01). */
    @Query(value = """
            SELECT *
              FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
             WHERE CODIGO_MEF = :codigoMef
               AND ACTIVO = :activo
             ORDER BY FECHA_VIG_INI DESC NULLS LAST,
                      CREATED_AT DESC NULLS LAST,
                      ID DESC
             FETCH FIRST 1 ROWS ONLY
            """, nativeQuery = true)
    Optional<ConceptoPlanilla>
    findByCodigoMefAndActivo(
            @Param("codigoMef") String codigoMef,
            @Param("activo") Integer activo);

    /** B3 — conceptos activos con un código PLAME SUNAT dado (muchos-a-uno). */
    List<ConceptoPlanilla>
    findByCodigoPlameSunatAndActivo(
            String codigoPlameSunat,
            Integer activo);

    /** B3 — conceptos activos con un código MCPP dado (muchos-a-uno). */
    List<ConceptoPlanilla>
    findByCodigoMcppAndActivo(
            String codigoMcpp,
            Integer activo);

    /**
     * SPEC_CONCEPTOS_PLANILLA §13 (V010_100) — siguiente valor de la secuencia del
     * código correlativo. El service formatea {@code CONC-} + LPAD(valor,4,'0').
     */
    @Query(value = "SELECT GESTIONRRHH.INDECI_SEQ_CONCEPTO_COD.NEXTVAL FROM DUAL",
            nativeQuery = true)
    Long nextCodigoCorrelativo();
}
