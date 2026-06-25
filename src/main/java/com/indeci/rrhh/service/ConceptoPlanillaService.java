package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.audit.entity.Auditoria;
import com.indeci.audit.repository.AuditoriaRepository;
import com.indeci.exception.ConceptoEnPlanillaCerradaException;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ConceptoHistorialDto;
import com.indeci.rrhh.dto.ConceptoPlanillaDto;
import com.indeci.rrhh.dto.ConceptoPlanillaResponseDto;
import com.indeci.rrhh.entity.CatalogoConceptoMgrh;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.ConceptoPlanillaTipo;
import com.indeci.rrhh.entity.ConceptoRtps;
import com.indeci.rrhh.entity.ConceptoTipoInterno;
import com.indeci.rrhh.repository.CatalogoConceptoMgrhRepository;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.ConceptoPlanillaTipoRepository;
import com.indeci.rrhh.repository.ConceptoRtpsRepository;
import com.indeci.rrhh.repository.ConceptoTipoInternoRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.PlanillaTipoRepository;

import jakarta.persistence.criteria.Predicate;

import lombok.RequiredArgsConstructor;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo de conceptos de planilla.
 *
 * <p>SPEC_CONCEPTOS_PLANILLA P1 (§10.2) — {@code guardar()}/{@code actualizar()}
 * persisten TODOS los campos del concepto (antes solo 4). Se añade el ciclo de
 * vida ({@code estado}) con endpoints de transición y un guard que impide editar
 * o transicionar un concepto ya usado en una planilla cerrada (§8/D5).</p>
 *
 * <p>En P1 el motor sigue leyendo {@code ACTIVO} (no {@code ESTADO}); por eso cada
 * transición sincroniza {@code activo} con el nuevo {@code estado}.</p>
 */
@Service
@RequiredArgsConstructor
public class ConceptoPlanillaService {

    // ============================
    // Estados del ciclo de vida (§8/D1)
    // ============================
    static final String BORRADOR    = "BORRADOR";
    static final String EN_REVISION = "EN_REVISION";
    static final String ACTIVO      = "ACTIVO";
    static final String CERRADO     = "CERRADO";
    static final String ANULADO     = "ANULADO";

    /** §13 — prefijo del código correlativo auto-generado (CONC-####). */
    static final String PREFIJO_CODIGO = "CONC-";

    /** §14 / P4 — modo de cálculo por defecto cuando el request no lo envía. */
    static final String MODO_CALCULO_DEFECTO = "RESULTADO_MOTOR";

    /** SPEC_HOMOLOGACION_MGRH — estados derivados de la homologación. */
    static final String HOMOLOGADO = "HOMOLOGADO";
    static final String PENDIENTE  = "PENDIENTE";

    private final ConceptoPlanillaRepository repository;
    private final ConceptoRtpsRepository rtpsRepository;
    private final CatalogoConceptoMgrhRepository catalogoMgrhRepository;
    private final ConceptoTipoInternoRepository tipoInternoRepository;
    private final MovimientoPlanillaDetalleRepository movimientoDetalleRepository;
    private final ConceptoPlanillaTipoRepository conceptoPlanillaTipoRepository;
    private final PlanillaTipoRepository planillaTipoRepository;
    private final AuditoriaContext auditoriaContext;
    private final AuditoriaRepository auditoriaRepository;

    // ============================
    // CREAR
    // ============================

    @Transactional
    @Auditable(accion = "CREAR_CONCEPTO_PLANILLA")
    public void guardar(ConceptoPlanillaDto dto) {

        // §15 / Fase A — valida la aplicabilidad a planilla (si "se incluye", exige ≥1).
        validarAplicabilidadPlanilla(dto);

        ConceptoPlanilla entity = new ConceptoPlanilla();

        aplicarCampos(entity, dto);

        // §13 — código auto: si no llega código (o llega en blanco), se genera un
        // correlativo CONC-#### desde la secuencia. Un código explícito (conceptos
        // técnicos/seed) se respeta tal cual (ya lo fijó aplicarCampos()).
        if (esBlank(dto.getCodigo())) {
            entity.setCodigo(generarCodigoCorrelativo());
        }

        // El concepto nace como BORRADOR; no afecta planilla hasta ACTIVO.
        // El motor en P1 lee ACTIVO, por lo que un borrador queda ACTIVO=0.
        entity.setEstado(BORRADOR);
        entity.setActivo(0);
        // P3 — primera versión del código (DEFAULT 1 en BD; se fija explícito por
        // consistencia con tests/lecturas en memoria).
        entity.setVersion(1);
        entity.setCreatedAt(LocalDateTime.now());

        ConceptoPlanilla guardado = repository.save(entity);

        // §15 / Fase A — reemplaza (aquí: crea) las asociaciones M:N a tipos de planilla.
        reemplazarPlanillaTipos(guardado.getId(), dto.getPlanillaTipos());

        auditoriaContext.setDetalle("Concepto planilla creado (BORRADOR): " + dto.getNombre());
    }

    // ============================
    // LISTAR
    // ============================

    /**
     * Conceptos ACTIVOS (espejo {@code ACTIVO=1}). Lo consume
     * {@code EmpleadoConceptoService.listarAsignables()} y demás flujos operativos;
     * por eso un BORRADOR/EN_REVISION (ACTIVO=0) NO aparece aquí ni se vuelve
     * asignable hasta activarse (criterio P2).
     */
    public List<ConceptoPlanillaResponseDto> listar() {
        return repository.findByActivo(1)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * SPEC_CONCEPTOS_PLANILLA P1/P3 — catálogo de gestión: todos los conceptos salvo
     * los ANULADO (incluye borradores y conceptos en revisión, con su {@code estado}
     * y chip RTPS). Es la lista que ve la pantalla de "Conceptos de Planilla".
     *
     * <p>P3 — versionado: por cada CÓDIGO se devuelve SOLO la versión vigente
     * (la de mayor vigencia/versión); el resto de versiones vive en el Historial.
     * Conceptos con código nulo (legacy) o sin versionar conservan el comportamiento
     * anterior (aparecen tal cual).</p>
     */
    public List<ConceptoPlanillaResponseDto> listarCatalogo() {
        // Agrupar por CÓDIGO y quedarnos con la versión vigente de cada uno.
        // Las filas sin código (null) se mantienen individualmente (no se colapsan).
        Map<String, ConceptoPlanilla> vigentePorCodigo = new LinkedHashMap<>();
        List<ConceptoPlanilla> sinCodigo = new ArrayList<>();

        for (ConceptoPlanilla c : repository.findByEstadoNot(ANULADO)) {
            String codigo = c.getCodigo();
            if (codigo == null || codigo.isBlank()) {
                sinCodigo.add(c);
                continue;
            }
            vigentePorCodigo.merge(codigo, c,
                    (actual, candidato) -> esMasVigente(candidato, actual) ? candidato : actual);
        }

        List<ConceptoPlanillaResponseDto> result = new ArrayList<>();
        vigentePorCodigo.values().forEach(c -> result.add(toResponse(c)));
        sinCodigo.forEach(c -> result.add(toResponse(c)));
        return result;
    }

    /**
     * Orden de "vigencia" entre dos versiones del mismo código: prioriza la mayor
     * FECHA_VIG_INI, luego la mayor VERSION, luego el mayor ID (mismo criterio que
     * el lookup defensivo {@code findByCodigoAndActivo}).
     */
    private boolean esMasVigente(ConceptoPlanilla candidato, ConceptoPlanilla actual) {
        int cmp = compareNullsFirst(candidato.getFechaVigIni(), actual.getFechaVigIni());
        if (cmp != 0) {
            return cmp > 0;
        }
        cmp = Integer.compare(nz(candidato.getVersion()), nz(actual.getVersion()));
        if (cmp != 0) {
            return cmp > 0;
        }
        return nz(candidato.getId()) > nz(actual.getId());
    }

    private static int compareNullsFirst(LocalDate a, LocalDate b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    // ============================
    // ACTUALIZAR
    // ============================

    @Transactional
    @Auditable(accion = "ACTUALIZAR_CONCEPTO_PLANILLA")
    public void actualizar(Long id, ConceptoPlanillaDto dto) {

        ConceptoPlanilla entity = obtener(id);

        // Guard §8/D5 — un concepto usado en planilla cerrada es inmutable.
        if (usadoEnPlanillaCerrada(id)) {
            throw new ConceptoEnPlanillaCerradaException();
        }

        // §15 / Fase A — valida la aplicabilidad a planilla (si "se incluye", exige ≥1).
        validarAplicabilidadPlanilla(dto);

        aplicarCampos(entity, dto);
        // estado/activo NO se tocan aquí: el flujo de estado va por las transiciones.

        repository.save(entity);

        // §15 / Fase A — reemplaza las asociaciones M:N por las del request.
        reemplazarPlanillaTipos(id, dto.getPlanillaTipos());

        auditoriaContext.setDetalle("Concepto planilla actualizado ID: " + id);
    }

    // ============================
    // ELIMINAR (baja lógica)
    // ============================

    @Transactional
    @Auditable(accion = "ELIMINAR_CONCEPTO_PLANILLA")
    public void eliminar(Long id) {
        ConceptoPlanilla entity = obtener(id);
        entity.setActivo(0);
        repository.save(entity);
        auditoriaContext.setDetalle("Concepto planilla eliminado ID: " + id);
    }

    // ============================
    // TRANSICIONES DE ESTADO (§8/D1)
    //   PLA_WRITE   : BORRADOR -> EN_REVISION
    //   PLA_APPROVE : EN_REVISION -> ACTIVO ; ACTIVO -> CERRADO ; * -> ANULADO
    // ============================

    @Transactional
    @Auditable(accion = "ENVIAR_REVISION_CONCEPTO_PLANILLA")
    public void enviarRevision(Long id) {
        transicionar(id, EN_REVISION, BORRADOR);
    }

    @Transactional
    @Auditable(accion = "ACTIVAR_CONCEPTO_PLANILLA")
    public void activar(Long id) {
        // SPEC_CONCEPTOS_PLANILLA P3 — supersede: antes de activar esta versión,
        // cerrar cualquier otra fila ACTIVO del mismo CÓDIGO para mantener el
        // invariante "1 fila ACTIVO por código" (evita NonUniqueResultException
        // en el motor). Se hace ANTES de la transición para que, si el origen no
        // es válido y transicionar() lanza, no quede el supersede a medias.
        ConceptoPlanilla entity = obtener(id);
        supersedeOtrasVersionesActivas(entity);
        transicionar(id, ACTIVO, EN_REVISION);
    }

    /**
     * SPEC_CONCEPTOS_PLANILLA P3 — al activar una versión, cualquier otra fila del
     * mismo CÓDIGO que esté en ACTIVO pasa a CERRADO (supersede). Garantiza el
     * invariante "1 ACTIVO por código". No se aplica a códigos nulos/en blanco.
     */
    private void supersedeOtrasVersionesActivas(ConceptoPlanilla nueva) {
        String codigo = nueva.getCodigo();
        if (codigo == null || codigo.isBlank()) {
            return;
        }
        repository.findByCodigoOrderByVersionDesc(codigo).stream()
                .filter(otra -> !otra.getId().equals(nueva.getId()))
                .filter(otra -> ACTIVO.equals(otra.getEstado()))
                .forEach(otra -> {
                    otra.setEstado(CERRADO);
                    otra.setActivo(0);
                    repository.save(otra);
                });
    }

    @Auditable(accion = "CERRAR_CONCEPTO_PLANILLA")
    public void cerrar(Long id) {
        transicionar(id, CERRADO, ACTIVO);
    }

    @Transactional
    @Auditable(accion = "ANULAR_CONCEPTO_PLANILLA")
    public void anular(Long id) {
        // Anulable desde BORRADOR | EN_REVISION | ACTIVO (no desde CERRADO/ANULADO).
        transicionar(id, ANULADO, BORRADOR, EN_REVISION, ACTIVO);
    }

    /**
     * Aplica una transición de estado validando el estado origen y sincronizando
     * {@code activo} como espejo legacy (ACTIVO→1; CERRADO/ANULADO→0; resto→0).
     * Bloquea si el concepto se usó en planilla cerrada (§8/D5).
     */
    private void transicionar(Long id, String destino, String... origenesPermitidos) {
        ConceptoPlanilla entity = obtener(id);

        if (usadoEnPlanillaCerrada(id)) {
            throw new ConceptoEnPlanillaCerradaException();
        }

        String actual = entity.getEstado() != null ? entity.getEstado() : BORRADOR;
        if (!esOrigenValido(actual, origenesPermitidos)) {
            throw new NegocioException(
                    "Transición no permitida: " + actual + " -> " + destino);
        }

        entity.setEstado(destino);
        entity.setActivo(ACTIVO.equals(destino) ? 1 : 0);
        repository.save(entity);

        auditoriaContext.setDetalle(
                "Concepto planilla ID " + id + ": " + actual + " -> " + destino);
    }

    private boolean esOrigenValido(String actual, String... origenesPermitidos) {
        for (String o : origenesPermitidos) {
            if (o.equals(actual)) {
                return true;
            }
        }
        return false;
    }

    // ============================
    // VERSIONADO (§12 / P3)
    // ============================

    /**
     * SPEC_CONCEPTOS_PLANILLA P3 — crea una NUEVA versión vigente del concepto
     * (flujo D5 cuando el concepto está usado en planilla cerrada: en vez de editar,
     * se emite una configuración vigente hacia adelante).
     *
     * <p>Clona TODOS los campos del concepto en una fila nueva con:
     * {@code VERSION = max(version del mismo CÓDIGO) + 1}, {@code FECHA_VIG_INI =
     * nuevaVigIni}, {@code FECHA_VIG_FIN = null}, {@code ESTADO = BORRADOR},
     * {@code activo = 0}. Al predecesor le fija {@code FECHA_VIG_FIN =
     * nuevaVigIni − 1 día}. Valida que no haya solapamiento de vigencias entre
     * versiones del mismo CÓDIGO.</p>
     *
     * @return el id de la nueva versión creada.
     */
    @Auditable(accion = "CREAR_VERSION_CONCEPTO_PLANILLA")
    public Long crearNuevaVersion(Long id, LocalDate nuevaVigIni) {
        if (nuevaVigIni == null) {
            throw new NegocioException("La fecha de inicio de vigencia es obligatoria.");
        }

        ConceptoPlanilla base = obtener(id);
        String codigo = base.getCodigo();
        if (codigo == null || codigo.isBlank()) {
            throw new NegocioException(
                    "El concepto no tiene CÓDIGO; no se puede versionar por código.");
        }

        List<ConceptoPlanilla> versiones = repository.findByCodigoOrderByVersionDesc(codigo);

        // No solapamiento: ninguna versión existente puede seguir vigente en/después
        // de nuevaVigIni (una versión está vigente hasta su FECHA_VIG_FIN; null = abierta).
        validarNoSolapamiento(versiones, nuevaVigIni);

        int maxVersion = versiones.stream()
                .map(ConceptoPlanilla::getVersion)
                .map(ConceptoPlanillaService::nz)
                .max(Integer::compareTo)
                .orElse(nz(base.getVersion()));

        // Cerrar la vigencia del predecesor (la versión vigente / sin fin) en
        // nuevaVigIni − 1 día.
        ConceptoPlanilla predecesor = resolverPredecesor(versiones, base);
        if (predecesor != null) {
            predecesor.setFechaVigFin(nuevaVigIni.minusDays(1));
            repository.save(predecesor);
        }

        ConceptoPlanilla nueva = clonar(base);
        nueva.setVersion(maxVersion + 1);
        nueva.setFechaVigIni(nuevaVigIni);
        nueva.setFechaVigFin(null);
        nueva.setEstado(BORRADOR);
        nueva.setActivo(0);
        nueva.setCreatedAt(LocalDateTime.now());
        ConceptoPlanilla guardada = repository.save(nueva);

        // §15 / Fase A — la nueva versión COPIA las asociaciones de tipo de planilla
        // de la versión origen (no se pierden al versionar; flujo D5).
        copiarPlanillaTipos(base.getId(), guardada.getId());

        auditoriaContext.setDetalle(
                "Nueva versión v" + guardada.getVersion() + " del concepto código "
                        + codigo + " (ID " + guardada.getId() + ") vigente desde " + nuevaVigIni);
        return guardada.getId();
    }

    /**
     * Valida que {@code nuevaVigIni} no solape con versiones existentes del código.
     *
     * <p>Una nueva versión siempre nace "hacia adelante": trunca al predecesor
     * abierto en {@code nuevaVigIni − 1}. Por eso NO se considera solapamiento que
     * exista una versión abierta empezada antes (esa se cerrará). Lo que SÍ se
     * rechaza es:</p>
     * <ul>
     *   <li>{@code nuevaVigIni} igual o anterior al inicio de cualquier versión
     *       existente (no se puede insertar "antes o sobre" una versión), y</li>
     *   <li>{@code nuevaVigIni} dentro de un rango YA CERRADO
     *       ({@code [vigIni, vigFin]}).</li>
     * </ul>
     */
    private void validarNoSolapamiento(List<ConceptoPlanilla> versiones, LocalDate nuevaVigIni) {
        for (ConceptoPlanilla v : versiones) {
            if (ANULADO.equals(v.getEstado())) {
                continue;
            }
            LocalDate ini = v.getFechaVigIni();
            LocalDate fin = v.getFechaVigFin();

            // No se puede iniciar en o antes del inicio de una versión existente.
            if (ini != null && !nuevaVigIni.isAfter(ini)) {
                throw new NegocioException(
                        "Solapamiento de vigencias: la nueva fecha " + nuevaVigIni
                                + " no puede ser anterior o igual al inicio de la versión v"
                                + v.getVersion() + " (" + ini + "). Elija una fecha posterior.");
            }
            // No puede caer dentro de un rango ya cerrado.
            if (fin != null && ini != null
                    && !nuevaVigIni.isBefore(ini) && !nuevaVigIni.isAfter(fin)) {
                throw new NegocioException(
                        "Solapamiento de vigencias: la fecha " + nuevaVigIni
                                + " cae dentro del rango cerrado de la versión v"
                                + v.getVersion() + " (" + ini + " a " + fin + ").");
            }
        }
    }

    /**
     * El predecesor a cerrar es la versión con mayor vigencia/versión (la "abierta").
     * Si por algún motivo no se identifica, se usa la fila base recibida.
     */
    private ConceptoPlanilla resolverPredecesor(List<ConceptoPlanilla> versiones,
                                                ConceptoPlanilla base) {
        return versiones.stream()
                .filter(v -> !ANULADO.equals(v.getEstado()))
                .reduce((a, b) -> esMasVigente(b, a) ? b : a)
                .orElse(base);
    }

    /** Copia todos los campos de configuración a una entidad nueva (sin id). */
    private ConceptoPlanilla clonar(ConceptoPlanilla src) {
        ConceptoPlanilla c = new ConceptoPlanilla();
        c.setCodigo(src.getCodigo());
        c.setNombre(src.getNombre());
        c.setTipo(src.getTipo());
        c.setNaturaleza(src.getNaturaleza());
        // §13 — clona el tipo funcional; tipoConcepto (motor) ya viene derivado y
        // consistente en la base, se copia tal cual (re-derivación = mismo valor).
        c.setTipoConceptoInterno(src.getTipoConceptoInterno());
        c.setTipoConcepto(src.getTipoConcepto());
        c.setCodigoMef(src.getCodigoMef());
        c.setCodigoSisper(src.getCodigoSisper());
        c.setCodigoPlameSunat(src.getCodigoPlameSunat());
        c.setCodigoMcpp(src.getCodigoMcpp());
        c.setCodigoTributoSunat(src.getCodigoTributoSunat());
        c.setRtpsCodigo(src.getRtpsCodigo());
        c.setAfectoIr5ta(src.getAfectoIr5ta());
        c.setAfectoAportePens(src.getAfectoAportePens());
        c.setAfectoEssalud(src.getAfectoEssalud());
        c.setEsMuc(src.getEsMuc());
        c.setEsCuc(src.getEsCuc());
        c.setRegimenAplicable(src.getRegimenAplicable());
        c.setEsProrrateable(src.getEsProrrateable());
        // §14 / P4 — la nueva versión hereda el modo de cálculo del predecesor.
        c.setModoCalculo(src.getModoCalculo());
        // SPEC_HOMOLOGACION_MGRH §C.2 — la nueva versión COPIA la homologación MGRH.
        c.setCatalogoConceptoMgrhId(src.getCatalogoConceptoMgrhId());
        c.setObservacionHomologacionMgrh(src.getObservacionHomologacionMgrh());
        c.setIncluyeEnPlanilla(src.getIncluyeEnPlanilla());
        return c;
    }

    // ============================
    // HISTORIAL (§12 / P3)
    // ============================

    /**
     * SPEC_CONCEPTOS_PLANILLA P3 — historial de un concepto: versiones del mismo
     * CÓDIGO + log de auditoría asociado a esas filas.
     *
     * <p>La auditoría se filtra por las acciones del ciclo de vida del concepto y
     * por el ID en el campo {@code detalle} (la entidad {@code Auditoria} no tiene
     * columna "entidad"; el detalle escrito por este service incluye el ID).</p>
     */
    @Transactional(readOnly = true)
    public ConceptoHistorialDto historial(Long id) {
        ConceptoPlanilla base = obtener(id);
        String codigo = base.getCodigo();

        List<ConceptoPlanilla> versiones = (codigo != null && !codigo.isBlank())
                ? repository.findByCodigoOrderByVersionDesc(codigo)
                : List.of(base);

        ConceptoHistorialDto dto = new ConceptoHistorialDto();
        dto.setVersiones(versiones.stream().map(this::toVersionItem).toList());
        dto.setAuditoria(cargarAuditoria(versiones));
        return dto;
    }

    private ConceptoHistorialDto.VersionItem toVersionItem(ConceptoPlanilla c) {
        ConceptoHistorialDto.VersionItem v = new ConceptoHistorialDto.VersionItem();
        v.setId(c.getId());
        v.setVersion(c.getVersion());
        v.setVigIni(c.getFechaVigIni());
        v.setVigFin(c.getFechaVigFin());
        v.setEstado(c.getEstado());
        v.setVigente(ACTIVO.equals(c.getEstado()));
        return v;
    }

    /** Acciones de ciclo de vida que audita este service (para el filtro del historial). */
    private static final List<String> ACCIONES_CONCEPTO = List.of(
            "CREAR_CONCEPTO_PLANILLA",
            "ACTUALIZAR_CONCEPTO_PLANILLA",
            "ELIMINAR_CONCEPTO_PLANILLA",
            "ENVIAR_REVISION_CONCEPTO_PLANILLA",
            "ACTIVAR_CONCEPTO_PLANILLA",
            "CERRAR_CONCEPTO_PLANILLA",
            "ANULAR_CONCEPTO_PLANILLA",
            "CREAR_VERSION_CONCEPTO_PLANILLA");

    /**
     * Reúne las entradas de auditoría de TODAS las versiones del código. Filtra por
     * acción de concepto y por el ID en el {@code detalle} (las transiciones graban
     * "Concepto planilla ID {id}: ..."; la creación graba "... ID {id} ..."). Usa
     * {@link AuditoriaRepository} (JpaSpecificationExecutor), patrón previsional.
     */
    private List<ConceptoHistorialDto.AuditoriaItem> cargarAuditoria(
            List<ConceptoPlanilla> versiones) {

        List<Long> ids = versiones.stream().map(ConceptoPlanilla::getId).toList();

        Specification<Auditoria> spec = (root, query, cb) -> {
            Predicate accionEsConcepto = root.get("accion").in(ACCIONES_CONCEPTO);
            Predicate detalleMencionaId = cb.disjunction();
            for (Long vid : ids) {
                // "ID 7" o "ID 7:" — se ancla con espacios para no casar 17/70.
                detalleMencionaId = cb.or(detalleMencionaId,
                        cb.like(root.get("detalle"), "%ID " + vid + ":%"),
                        cb.like(root.get("detalle"), "%ID " + vid + " %"));
            }
            query.orderBy(cb.desc(root.get("fecha")));
            return cb.and(accionEsConcepto, detalleMencionaId);
        };

        return auditoriaRepository.findAll(spec).stream()
                .map(a -> {
                    ConceptoHistorialDto.AuditoriaItem item =
                            new ConceptoHistorialDto.AuditoriaItem();
                    item.setAccion(a.getAccion());
                    item.setUsuario(a.getUsuario());
                    item.setFecha(a.getFecha());
                    item.setDetalle(a.getDetalle());
                    return item;
                })
                .toList();
    }

    // ============================
    // GUARD — uso en planilla cerrada (§8/D5)
    // ============================

    /**
     * {@code true} si el concepto tiene al menos un detalle en una planilla
     * cuyo período está CERRADO o APROBADO (inmutable).
     */
    boolean usadoEnPlanillaCerrada(Long conceptoId) {
        return movimientoDetalleRepository.countEnPlanillaCerrada(conceptoId) > 0;
    }

    // ============================
    // MAPEOS / HELPERS
    // ============================

    private ConceptoPlanilla obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NegocioException("Concepto no encontrado"));
    }

    // ============================
    // §13 — DERIVACIÓN + CÓDIGO AUTO
    // ============================

    /**
     * §13 — deriva el {@code TIPO_CONCEPTO} (motor) desde la {@code CLASIFICACION_MOTOR}
     * de la fila del catálogo "Tipo de Concepto" (SISPER) cuyo CODIGO = {@code
     * tipoConceptoInterno}. Data-driven (DRY): lo reutilizan guardar/actualizar.
     *
     * @return la clasificación del motor, o {@code null} si {@code tipoConceptoInterno}
     *         es blanco (el caller conserva entonces el {@code tipoConcepto} recibido).
     */
    private String derivarTipoConceptoMotor(String tipoConceptoInterno) {
        if (esBlank(tipoConceptoInterno)) {
            return null;
        }
        return tipoInternoRepository.findById(tipoConceptoInterno)
                .map(ConceptoTipoInterno::getClasificacionMotor)
                .orElseThrow(() -> new NegocioException(
                        "Tipo de Concepto no válido: " + tipoConceptoInterno));
    }

    /** §13 — {@code CONC-} + LPAD(nextval,4,'0') (ej. CONC-0007). */
    private String generarCodigoCorrelativo() {
        Long siguiente = repository.nextCodigoCorrelativo();
        return PREFIJO_CODIGO + String.format("%04d", siguiente);
    }

    private static boolean esBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Copia TODOS los campos editables del DTO a la entidad (no toca estado/activo). */
    private void aplicarCampos(ConceptoPlanilla e, ConceptoPlanillaDto dto) {
        e.setCodigo(dto.getCodigo());
        e.setNombre(dto.getNombre());
        e.setTipo(dto.getTipo());
        e.setNaturaleza(dto.getNaturaleza());

        // §13 — "Tipo de Concepto" funcional (SISPER) + derivación del motor.
        e.setTipoConceptoInterno(dto.getTipoConceptoInterno());
        // El TIPO_CONCEPTO (motor) se DERIVA de la CLASIFICACION_MOTOR de la fila
        // del catálogo. Si no hay tipoConceptoInterno, se conserva el tipoConcepto
        // recibido (compat con conceptos/seed que ya lo envían directo).
        String tipoConceptoMotor = derivarTipoConceptoMotor(dto.getTipoConceptoInterno());
        e.setTipoConcepto(tipoConceptoMotor != null ? tipoConceptoMotor : dto.getTipoConcepto());

        e.setCodigoMef(dto.getCodigoMef());
        e.setCodigoSisper(dto.getCodigoSisper());
        e.setCodigoPlameSunat(dto.getCodigoPlameSunat());
        e.setCodigoMcpp(dto.getCodigoMcpp());
        e.setCodigoTributoSunat(dto.getCodigoTributoSunat());
        e.setRtpsCodigo(dto.getRtpsCodigo());

        e.setAfectoIr5ta(dto.getAfectoIr5ta());
        e.setAfectoAportePens(dto.getAfectoAportePens());
        e.setAfectoEssalud(dto.getAfectoEssalud());
        e.setEsMuc(dto.getEsMuc());
        e.setEsCuc(dto.getEsCuc());

        e.setRegimenAplicable(dto.getRegimenAplicable());
        e.setFechaVigIni(dto.getFechaVigIni());
        e.setFechaVigFin(dto.getFechaVigFin());

        e.setEsProrrateable(dto.getEsProrrateable());

        // §14 / P4 — modo de cálculo (metadata; el motor no se ramifica por él).
        // null/blank -> 'RESULTADO_MOTOR' (default, alineado con la BD).
        e.setModoCalculo(esBlank(dto.getModoCalculo())
                ? MODO_CALCULO_DEFECTO : dto.getModoCalculo());

        // SPEC_HOMOLOGACION_MGRH §C.2 — homologación MGRH/MEF (FK nullable, opcional).
        // No obligatoria: null deja el concepto Pendiente; no bloquea crear/editar/activar.
        e.setCatalogoConceptoMgrhId(dto.getCatalogoConceptoMgrhId());
        e.setObservacionHomologacionMgrh(dto.getObservacionHomologacionMgrh());
        // §15 — ¿se incluye en planilla de pago? Normaliza a 'S' | 'N' (default 'S').
        e.setIncluyeEnPlanilla(normalizaIncluye(dto.getIncluyeEnPlanilla()));
    }

    private ConceptoPlanillaResponseDto toResponse(ConceptoPlanilla e) {
        ConceptoPlanillaResponseDto dto = new ConceptoPlanillaResponseDto();

        dto.setId(e.getId());
        dto.setCodigo(e.getCodigo());
        dto.setNombre(e.getNombre());
        dto.setTipo(e.getTipo());
        dto.setNaturaleza(e.getNaturaleza());
        dto.setActivo(e.getActivo());

        dto.setCodigoMef(e.getCodigoMef());
        dto.setCodigoSisper(e.getCodigoSisper());
        dto.setTipoConcepto(e.getTipoConcepto());
        dto.setTipoConceptoInterno(e.getTipoConceptoInterno());

        dto.setCodigoPlameSunat(e.getCodigoPlameSunat());
        dto.setCodigoMcpp(e.getCodigoMcpp());
        dto.setCodigoTributoSunat(e.getCodigoTributoSunat());
        dto.setAfectoIr5ta(e.getAfectoIr5ta());
        dto.setAfectoAportePens(e.getAfectoAportePens());
        dto.setAfectoEssalud(e.getAfectoEssalud());
        dto.setEsMuc(e.getEsMuc());
        dto.setEsCuc(e.getEsCuc());
        dto.setRegimenAplicable(e.getRegimenAplicable());
        dto.setFechaVigIni(e.getFechaVigIni());
        dto.setFechaVigFin(e.getFechaVigFin());
        dto.setEsProrrateable(e.getEsProrrateable());
        // §14 / P4 — modo de cálculo (metadata) expuesto para el wizard.
        dto.setModoCalculo(e.getModoCalculo());

        // P1 — ciclo de vida + RTPS.
        dto.setEstado(e.getEstado());
        // P3 — versión vigente (display).
        dto.setVersion(e.getVersion());
        dto.setRtpsCodigo(e.getRtpsCodigo());
        if (e.getRtpsCodigo() != null) {
            rtpsRepository.findById(e.getRtpsCodigo())
                    .map(ConceptoRtps::getDescripcion)
                    .ifPresent(dto::setRtpsDescripcion);
        }

        // §15 / Fase A — códigos de tipos de planilla asociados (M:N).
        if (e.getId() != null) {
            dto.setPlanillaTipos(
                    conceptoPlanillaTipoRepository.findByConceptoPlanillaId(e.getId())
                            .stream()
                            .map(ConceptoPlanillaTipo::getPlanillaTipoCodigo)
                            .toList());
        }

        // SPEC_HOMOLOGACION_MGRH §C.2 — homologación MGRH/MEF: FK + estado derivado
        // (HOMOLOGADO/PENDIENTE) + resumen read-only para el chip/detalle sin segunda llamada.
        Long mgrhId = e.getCatalogoConceptoMgrhId();
        dto.setCatalogoConceptoMgrhId(mgrhId);
        dto.setObservacionHomologacionMgrh(e.getObservacionHomologacionMgrh());
        dto.setIncluyeEnPlanilla(e.getIncluyeEnPlanilla());
        dto.setEstadoHomologacionMgrh(mgrhId != null ? HOMOLOGADO : PENDIENTE);
        if (mgrhId != null) {
            catalogoMgrhRepository.findById(mgrhId)
                    .map(this::toMgrhResumen)
                    .ifPresent(dto::setMgrhResumen);
        }

        return dto;
    }

    /** Resumen mínimo del concepto MGRH homologado (display). */
    private ConceptoPlanillaResponseDto.MgrhResumen toMgrhResumen(CatalogoConceptoMgrh m) {
        ConceptoPlanillaResponseDto.MgrhResumen r = new ConceptoPlanillaResponseDto.MgrhResumen();
        r.setId(m.getId());
        r.setTipo(m.getTipo());
        r.setCodigoConceptoMgrh(m.getCodigoConceptoMgrh());
        r.setDescripcionNorma(m.getDescripcionNorma());
        return r;
    }

    // ============================
    // §15 / Fase A — ASOCIACIÓN M:N CONCEPTO ↔ TIPO DE PLANILLA
    // ============================

    /**
     * Valida la aplicabilidad a planilla según "¿se incluye en planilla de pago?":
     * si SÍ (default), exige ≥1 tipo de planilla; si NO, admite 0 (solo configuración
     * / cálculo / control). En ambos casos valida que cada código exista en el catálogo
     * activo {@code INDECI_PLANILLA_TIPO} (la FK también lo garantiza; el mensaje claro
     * se da antes de tocar la BD).
     */
    private void validarAplicabilidadPlanilla(ConceptoPlanillaDto dto) {
        List<String> planillaTipos = dto.getPlanillaTipos();
        // "S" (default) exige ≥1 planilla; "N" (solo config/cálculo/control) admite 0.
        if (seIncluyeEnPlanilla(dto.getIncluyeEnPlanilla())
                && (planillaTipos == null || planillaTipos.isEmpty())) {
            throw new NegocioException("Debe asociar al menos una planilla.");
        }
        if (planillaTipos != null) {
            for (String codigo : planillaTipos) {
                if (esBlank(codigo)) {
                    throw new NegocioException("Código de tipo de planilla en blanco.");
                }
                if (!planillaTipoRepository.existsById(codigo)) {
                    throw new NegocioException("Tipo de planilla no válido: " + codigo + ".");
                }
            }
        }
    }

    /** {@code true} salvo que el flag sea explícitamente 'N' (default = incluido en planilla). */
    private static boolean seIncluyeEnPlanilla(String incluye) {
        return !"N".equalsIgnoreCase(incluye == null ? "" : incluye.trim());
    }

    /** Normaliza el flag "¿se incluye en planilla?" a 'S' | 'N' (default 'S'). */
    private static String normalizaIncluye(String incluye) {
        return seIncluyeEnPlanilla(incluye) ? "S" : "N";
    }

    /**
     * Reemplaza por completo las asociaciones M:N del concepto: borra las existentes
     * e inserta las del request (sin duplicados, preservando el orden recibido).
     */
    private void reemplazarPlanillaTipos(Long conceptoId, List<String> planillaTipos) {
        conceptoPlanillaTipoRepository.deleteByConceptoPlanillaId(conceptoId);
        guardarAsociaciones(conceptoId, planillaTipos);
    }

    /** Copia las asociaciones de {@code origenId} a {@code destinoId} (versionado). */
    private void copiarPlanillaTipos(Long origenId, Long destinoId) {
        List<String> codigos = conceptoPlanillaTipoRepository
                .findByConceptoPlanillaId(origenId)
                .stream()
                .map(ConceptoPlanillaTipo::getPlanillaTipoCodigo)
                .toList();
        guardarAsociaciones(destinoId, codigos);
    }

    /** Inserta una fila por código distinto (DRY para reemplazar y copiar). */
    private void guardarAsociaciones(Long conceptoId, List<String> planillaTipos) {
        if (planillaTipos == null) {
            return;
        }
        planillaTipos.stream()
                .filter(c -> !esBlank(c))
                .distinct()
                .forEach(codigo -> {
                    ConceptoPlanillaTipo asoc = new ConceptoPlanillaTipo();
                    asoc.setConceptoPlanillaId(conceptoId);
                    asoc.setPlanillaTipoCodigo(codigo);
                    conceptoPlanillaTipoRepository.save(asoc);
                });
    }
}
