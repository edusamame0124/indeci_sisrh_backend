package com.indeci.rrhh.vinculacion.importacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoBanco;
import com.indeci.rrhh.entity.EmpleadoPension;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.EmpleadoPuesto;
import com.indeci.rrhh.entity.EmpleadoSaludEps;
import com.indeci.rrhh.entity.FormacionAcademica;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.repository.EmpleadoBancoRepository;
import com.indeci.rrhh.repository.EmpleadoPensionRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPuestoRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EmpleadoSaludEpsRepository;
import com.indeci.rrhh.repository.FormacionAcademicaRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.service.EmpleadoPlanillaService;
import com.indeci.rrhh.service.IncrementosDsCalculoService;
import com.indeci.rrhh.vinculacion.importacion.CatalogoTextResolver.Sesion;
import com.indeci.rrhh.vinculacion.importacion.PensionExcelParser.PensionLeida;

import lombok.RequiredArgsConstructor;

/**
 * Escribe una fila del Excel en las 7 entidades que componen la vinculación.
 *
 * <p><b>Llave de upsert: DNI + N.° de contrato.</b> Con ella, re-subir el mismo archivo
 * actualiza en vez de duplicar (idempotencia). La persona y el empleado se reutilizan si el
 * DNI ya existe: un servidor con varios vínculos secuenciales no se duplica.
 *
 * <p><b>Transacción por fila</b> ({@link Propagation#REQUIRES_NEW}): si una fila falla, se
 * revierte solo esa; las demás se importan. Es el comportamiento que exige una migración de
 * 617 filas — un dato malo no puede tumbar la carga completa.
 *
 * <p>Lo que este servicio <b>no</b> escribe, porque el sistema lo deriva:
 * <ul>
 *   <li>Estado del vínculo → {@code VinculoEstadoResolver} (de las fechas).</li>
 *   <li>Sueldo básico → el motor (monto de contrato + incrementos D.S.).</li>
 *   <li>Tipo de persona MEF → del régimen.</li>
 *   <li>Edad, provincia, departamento, tiempo de servicios → calculados.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class VinculacionUpsertService {

    private static final String ORIGEN = "IMPORT_VINCULACION";
    private static final int ACTIVO = 1;

    private final PersonaRepository personaRepository;
    private final EmpleadoRepository empleadoRepository;
    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;
    private final EmpleadoPensionRepository empleadoPensionRepository;
    private final EmpleadoPuestoRepository empleadoPuestoRepository;
    private final EmpleadoBancoRepository empleadoBancoRepository;
    private final FormacionAcademicaRepository formacionAcademicaRepository;
    private final EmpleadoSaludEpsRepository empleadoSaludEpsRepository;
    private final PensionExcelParser pensionParser;
    private final DiccionarioEquivalencias diccionario;
    private final IncrementosDsCalculoService incrementosDsCalculoService;

    /** Qué pasó con una fila. */
    public record Resultado(int numeroFila, String dni, boolean creado) {}

    /**
     * Importa una fila. Debe invocarse solo con filas sin errores de validación.
     *
     * @param fila   fila cruda leída del Excel
     * @param sesion índices de catálogo de esta importación
     * @return si el vínculo se creó o se actualizó
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Resultado importar(VinculacionRowRaw fila, Sesion sesion) {
        final String dni = TextoNormalizador.padCeros(fila.digitos(VinculacionColumna.DNI), 8);

        final Persona persona = upsertPersona(fila, sesion, dni);
        final Empleado empleado = upsertEmpleado(fila, persona);
        final boolean creado = upsertVinculo(fila, sesion, empleado);

        upsertPension(fila, sesion, empleado);
        upsertPuesto(fila, sesion, empleado);
        upsertBanco(fila, sesion, empleado);
        upsertFormacion(fila, sesion, empleado);
        upsertSaludEps(fila, empleado);

        return new Resultado(fila.getNumeroFila(), dni, creado);
    }

    // ------------------------------------------------------------------ Persona
    private Persona upsertPersona(VinculacionRowRaw fila, Sesion sesion, String dni) {
        final Persona persona = personaRepository.findByDni(dni).orElseGet(() -> {
            final Persona nueva = new Persona();
            nueva.setDni(dni);
            nueva.setCreatedAt(LocalDateTime.now());
            return nueva;
        });

        persona.setNombreCompleto(fila.texto(VinculacionColumna.NOMBRE_COMPLETO));
        aplicarSiPresente(fila.fecha(VinculacionColumna.FECHA_NACIMIENTO),
                f -> persona.setFechaNacimiento(java.sql.Date.valueOf(f)));
        aplicarSiPresente(fila.texto(VinculacionColumna.CORREO_PERSONAL), persona::setEmail);
        aplicarSiPresente(fila.texto(VinculacionColumna.CORREO_INSTITUCIONAL),
                persona::setCorreoInstitucional);
        aplicarSiPresente(fila.texto(VinculacionColumna.TELEFONO), persona::setTelefono);
        aplicarSiPresente(fila.texto(VinculacionColumna.DIRECCION), persona::setDireccion);
        aplicarSiPresente(fila.digitos(VinculacionColumna.RUC), persona::setRuc);
        aplicarSiPresente(
                diccionario.canonico(DiccionarioEquivalencias.Catalogo.NACIONALIDAD,
                        fila.texto(VinculacionColumna.NACIONALIDAD)),
                persona::setNacionalidad);

        sesion.sexo(fila.texto(VinculacionColumna.SEXO)).id(persona::setSexoId);
        sesion.estadoCivil(fila.texto(VinculacionColumna.ESTADO_CIVIL)).id(persona::setEstadoCivilId);
        sesion.profesion(fila.texto(VinculacionColumna.PROFESION)).id(persona::setProfesionId);
        sesion.gradoAcademico(fila.texto(VinculacionColumna.GRADO_ACADEMICO))
                .id(persona::setGradoAcademicoId);
        sesion.nivelInstruccion(fila.texto(VinculacionColumna.NIVEL_INSTRUCCION))
                .id(persona::setNivelInstruccionId);
        sesion.distrito(fila.texto(VinculacionColumna.DISTRITO)).ifPresent(persona::setDistritoId);

        persona.setUpdatedAt(LocalDateTime.now());
        return personaRepository.save(persona);
    }

    // ----------------------------------------------------------------- Empleado
    private Empleado upsertEmpleado(VinculacionRowRaw fila, Persona persona) {
        final Empleado empleado = empleadoRepository.findByPersonaId(persona.getId())
                .orElseGet(() -> {
                    final Empleado nuevo = new Empleado();
                    nuevo.setPersonaId(persona.getId());
                    nuevo.setEstado("ACTIVO");
                    nuevo.setCreatedAt(LocalDateTime.now());
                    return nuevo;
                });

        aplicarSiPresente(fila.clave(VinculacionColumna.CLASE_PERSONAL), empleado::setClasePersonal);
        aplicarSiPresente(fila.texto(VinculacionColumna.CONADIS), empleado::setConadisCodigo);
        final Boolean eps = fila.logico(VinculacionColumna.TIENE_EPS);
        if (eps != null) {
            empleado.setHasEps(Boolean.TRUE.equals(eps) ? "S" : "N");
        }
        return empleadoRepository.save(empleado);
    }

    // ------------------------------------------------------------------ Vínculo
    /** @return {@code true} si el vínculo se creó; {@code false} si se actualizó. */
    private boolean upsertVinculo(VinculacionRowRaw fila, Sesion sesion, Empleado empleado) {
        final String numeroContrato = fila.texto(VinculacionColumna.NUMERO_CONTRATO);

        final Optional<EmpleadoPlanilla> existente =
                empleadoPlanillaRepository.findByEmpleadoId(empleado.getId()).stream()
                        .filter(v -> numeroContrato.equalsIgnoreCase(v.getNumeroContrato()))
                        .findFirst();

        final EmpleadoPlanilla vinculo = existente.orElseGet(() -> {
            final EmpleadoPlanilla nuevo = new EmpleadoPlanilla();
            nuevo.setEmpleadoId(empleado.getId());
            nuevo.setNumeroContrato(numeroContrato);
            nuevo.setActivo(ACTIVO);
            nuevo.setCreatedAt(LocalDateTime.now());
            return nuevo;
        });

        final String airhsp = TextoNormalizador.padCeros(
                fila.digitos(VinculacionColumna.CODIGO_AIRHSP), 6);
        vinculo.setCodigoAirhsp(airhsp);
        // La pantalla de Config. Remunerativa muestra/edita el "Código Plaza AIRHSP" desde
        // REGISTRO_PLAZA_AIRHSP (no CODIGO_AIRHSP, que la UI fija en '000000'). Se escribe el
        // mismo valor en ambos para que el dato importado sea visible en el formulario.
        vinculo.setRegistroPlazaAirhsp(airhsp);

        final BigDecimal monto = fila.numero(VinculacionColumna.MONTO_CONTRATO);
        if (monto != null) {
            vinculo.setMontoContrato(monto.doubleValue());
        }

        sesion.regimenLaboral(fila.texto(VinculacionColumna.REGIMEN_LABORAL))
                .id(vinculo::setRegimenLaboralId);
        sesion.tipoContrato(fila.texto(VinculacionColumna.TIPO_CONTRATO))
                .id(vinculo::setTipoContratoId);
        sesion.modalidadCas(fila.texto(VinculacionColumna.MODALIDAD_CAS))
                .id(vinculo::setModalidadCasId);

        // Sueldo básico = monto contrato + incrementos D.S. (misma fórmula que el formulario).
        // El motor toma la base remunerativa de aquí; sin esto el empleado no tiene
        // "remuneración vigente" y no es elegible ni calculable en planilla.
        if (monto != null && monto.signum() > 0 && vinculo.getRegimenLaboralId() != null) {
            vinculo.setSueldoBasico(incrementosDsCalculoService.calcular(
                    vinculo.getRegimenLaboralId(), vinculo.getCondicionLaboralId(), monto,
                    fila.fecha(VinculacionColumna.FECHA_INICIO_CONTRATO))
                    .remuneracionMensual().doubleValue());
        }

        // Solo se guarda para SERVIR: en CAS el grupo de servidor civil no aplica.
        final String regimen = fila.clave(VinculacionColumna.REGIMEN_LABORAL);
        if (com.indeci.rrhh.service.GeneradorPlanillaService.esRegimenServir(regimen)) {
            vinculo.setGrupoServidorCivil(fila.clave(VinculacionColumna.GRUPO_SERVIDOR_CIVIL));
        }

        vinculo.setEsConfianza(esConfianza(fila) ? 1 : 0);
        vinculo.setEsTeletrabajador(Boolean.TRUE.equals(
                fila.logico(VinculacionColumna.ES_TELETRABAJADOR)) ? 1 : 0);

        aplicarSiPresente(fila.texto(VinculacionColumna.NRO_CONVOCATORIA), v -> {
            // '0' significa "sin convocatoria" (personal antiguo), no un número de proceso.
            vinculo.setNroConvocatoria("0".equals(v) ? null : v);
        });
        aplicarSiPresente(fila.texto(VinculacionColumna.CONDICION_LABORAL),
                vinculo::setBaseLegalVinculo);
        aplicarSiPresente(fila.texto(VinculacionColumna.META), vinculo::setMeta);
        aplicarSiPresente(fila.texto(VinculacionColumna.FUENTE_FINANCIAMIENTO),
                vinculo::setFuenteFinanciamiento);
        aplicarSiPresente(fila.texto(VinculacionColumna.CENTRO_COSTO), vinculo::setCentroCosto);

        vinculo.setFechaIngreso(fila.fecha(VinculacionColumna.FECHA_INGRESO));
        vinculo.setFechaInicioContrato(fila.fecha(VinculacionColumna.FECHA_INICIO_CONTRATO));
        vinculo.setFechaInicio(fila.fecha(VinculacionColumna.FECHA_INICIO_CONTRATO));
        vinculo.setFechaFin(fila.fecha(VinculacionColumna.FECHA_FIN));
        vinculo.setFechaCese(fila.fecha(VinculacionColumna.FECHA_CESE));
        aplicarSiPresente(fila.texto(VinculacionColumna.MOTIVO_CESE), vinculo::setMotivoCese);
        aplicarSiPresente(fila.texto(VinculacionColumna.DOCUMENTO_CESE), vinculo::setDocumentoCese);

        aplicarSiPresente(fila.texto(VinculacionColumna.DOCUMENTO_ORIGEN_TIPO),
                vinculo::setDocumentoOrigenTipo);
        aplicarSiPresente(fila.texto(VinculacionColumna.DOCUMENTO_ORIGEN_NUMERO),
                vinculo::setDocumentoOrigenNumero);
        vinculo.setDocumentoOrigenFecha(fila.fecha(VinculacionColumna.DOCUMENTO_ORIGEN_FECHA));

        aplicarSiPresente(fila.numero(VinculacionColumna.NUM_HIJOS),
                n -> vinculo.setNumHijos(n.intValue()));
        final Boolean asigFamiliar = fila.logico(VinculacionColumna.TIENE_ASIGNACION_FAMILIAR);
        if (asigFamiliar != null) {
            vinculo.setTieneAsignacionFamiliar(Boolean.TRUE.equals(asigFamiliar) ? 1 : 0);
        }

        vinculo.setObservacion(ORIGEN);
        vinculo.setUpdatedAt(LocalDateTime.now());
        empleadoPlanillaRepository.save(vinculo);

        reconciliarVigenciaUnica(empleado.getId());
        return existente.isEmpty();
    }

    /**
     * Un solo vínculo vigente por empleado (SERVIR/MEF). Deja VIGENTE el contrato de inicio más
     * reciente y CIERRA los demás vigentes (sin cese) con el sello de transición: fecha de cese
     * = inicio del vigente − 1 día, motivo {@code TRANSICIÓN DE CONTRATO} y SIN documento → no
     * habilita LBS (VinculoEstadoResolver.habilitaLbs exige documento).
     *
     * <p>Idempotente: re-importar produce el mismo resultado. Resuelve los duplicados que el
     * import creó al bypasear el guard de {@code EmpleadoPlanillaService.guardar()}.
     */
    private void reconciliarVigenciaUnica(Long empleadoId) {
        final List<EmpleadoPlanilla> vinculos =
                empleadoPlanillaRepository.findByEmpleadoIdAndActivo(empleadoId, ACTIVO);
        if (vinculos.size() <= 1) {
            return;
        }
        final EmpleadoPlanilla vigente = vinculos.stream()
                .max(Comparator.comparing(EmpleadoPlanilla::getFechaInicioContrato,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElseThrow();
        final LocalDate cierre = vigente.getFechaInicioContrato() != null
                ? vigente.getFechaInicioContrato().minusDays(1)
                : LocalDate.now();

        for (EmpleadoPlanilla v : vinculos) {
            if (v.getId().equals(vigente.getId()) || v.getFechaCese() != null) {
                continue;
            }
            v.setFechaCese(cierre);
            v.setMotivoCese(EmpleadoPlanillaService.MOTIVO_CESE_TRANSICION);
            v.setDocumentoCese(null);
            v.setUpdatedAt(LocalDateTime.now());
            empleadoPlanillaRepository.save(v);
        }
    }

    /** La columna admite 'S'/'N' y también el literal 'CONFIANZA' que usa RR.HH. */
    private boolean esConfianza(VinculacionRowRaw fila) {
        final Boolean logico = fila.logico(VinculacionColumna.ES_CONFIANZA);
        if (logico != null) {
            return logico;
        }
        final String texto = fila.clave(VinculacionColumna.ES_CONFIANZA);
        return texto != null && texto.contains("CONFIANZA");
    }

    // ------------------------------------------------------------------ Pensión
    private void upsertPension(VinculacionRowRaw fila, Sesion sesion, Empleado empleado) {
        final PensionLeida leida = pensionParser.parsear(fila);
        if (leida.sistema() == null) {
            return;
        }
        final EmpleadoPension pension = empleadoPensionRepository
                .findFirstByEmpleadoIdAndActivo(empleado.getId(), ACTIVO)
                .orElseGet(() -> {
                    final EmpleadoPension nueva = new EmpleadoPension();
                    nueva.setEmpleadoId(empleado.getId());
                    nueva.setActivo(ACTIVO);
                    nueva.setCreatedAt(LocalDateTime.now());
                    return nueva;
                });

        // El catálogo guarda la AFP concreta; para ONP/CPMP el sistema es el valor.
        final String aResolver = leida.esAfp() ? leida.afp() : leida.sistema();
        sesion.regimenPensionario(aResolver).id(pension::setRegimenPensionarioId);
        pension.setTipoRegimen(leida.sistema());
        aplicarSiPresente(fila.texto(VinculacionColumna.CUSPP), pension::setCuspp);
        aplicarSiPresente(leida.condicionEspecialAfp(), pension::setCondicionEspecialAfp);
        aplicarSiPresente(fila.fecha(VinculacionColumna.FECHA_AFILIACION), pension::setFechaInicio);

        empleadoPensionRepository.save(pension);
    }

    // ------------------------------------------------------------------- Puesto
    private void upsertPuesto(VinculacionRowRaw fila, Sesion sesion, Empleado empleado) {
        final EmpleadoPuesto puesto = empleadoPuestoRepository
                .findFirstByEmpleadoIdAndActivo(empleado.getId(), ACTIVO)
                .orElseGet(() -> {
                    final EmpleadoPuesto nuevo = new EmpleadoPuesto();
                    nuevo.setEmpleadoId(empleado.getId());
                    nuevo.setActivo(ACTIVO);
                    nuevo.setCreatedAt(LocalDateTime.now());
                    return nuevo;
                });

        sesion.cargo(fila.texto(VinculacionColumna.CARGO)).id(puesto::setCargoId);
        sesion.nivel(fila.texto(VinculacionColumna.NIVEL)).id(puesto::setNivelId);
        sesion.sede(fila.texto(VinculacionColumna.SEDE)).id(puesto::setSedeId);
        sesion.oficina(fila.texto(VinculacionColumna.OFICINA)).id(puesto::setOficinaId);
        resolverJefe(fila).ifPresent(puesto::setJefeId);
        if (puesto.getFechaInicio() == null) {
            puesto.setFechaInicio(fila.fecha(VinculacionColumna.FECHA_INICIO_CONTRATO));
        }
        empleadoPuestoRepository.save(puesto);
    }

    /**
     * Resuelve el "DNI del jefe inmediato" al id de su empleado (lo que llena el input
     * "Jefe inmediato" de Config. Remunerativa). Si la columna está vacía o el jefe aún no
     * existe en el sistema, se deja sin jefe (no rompe): al re-importar con todos cargados,
     * el jefe se resuelve.
     */
    private Optional<Long> resolverJefe(VinculacionRowRaw fila) {
        final String jefeDni = TextoNormalizador.padCeros(
                fila.digitos(VinculacionColumna.JEFE_DNI), 8);
        if (jefeDni == null) {
            return Optional.empty();
        }
        return personaRepository.findByDni(jefeDni)
                .flatMap(p -> empleadoRepository.findByPersonaId(p.getId()))
                .map(Empleado::getId);
    }

    // -------------------------------------------------------------------- Banco
    private void upsertBanco(VinculacionRowRaw fila, Sesion sesion, Empleado empleado) {
        final String cuenta = fila.digitos(VinculacionColumna.NUMERO_CUENTA);
        if (cuenta == null) {
            return;
        }
        final EmpleadoBanco banco = empleadoBancoRepository
                .findByEmpleadoIdAndEsCuentaPlanillaAndActivo(empleado.getId(), ACTIVO, ACTIVO)
                .orElseGet(() -> {
                    final EmpleadoBanco nuevo = new EmpleadoBanco();
                    nuevo.setEmpleadoId(empleado.getId());
                    nuevo.setEsCuentaPlanilla(ACTIVO);
                    nuevo.setActivo(ACTIVO);
                    nuevo.setCreatedAt(LocalDateTime.now());
                    return nuevo;
                });

        sesion.banco(fila.texto(VinculacionColumna.BANCO)).id(banco::setBankId);
        banco.setNumeroCuenta(cuenta);
        aplicarSiPresente(fila.digitos(VinculacionColumna.CCI), banco::setCci);
        empleadoBancoRepository.save(banco);
    }

    // ---------------------------------------------------------------- Formación
    /**
     * La formación es 1:N, pero el Excel trae una fila por empleado: se registra el grado
     * principal declarado. Cargas de varios grados van por el módulo de Legajo.
     */
    private void upsertFormacion(VinculacionRowRaw fila, Sesion sesion, Empleado empleado) {
        final String institucion = fila.texto(VinculacionColumna.ENTIDAD_UNIVERSIDAD);
        if (institucion == null) {
            return;
        }
        final List<FormacionAcademica> existentes = formacionAcademicaRepository
                .findByEmpleadoIdAndActivoOrderByFechaFinDesc(empleado.getId(), ACTIVO);

        final FormacionAcademica formacion = existentes.stream()
                .filter(f -> institucion.equalsIgnoreCase(f.getInstitucion()))
                .findFirst()
                .orElseGet(() -> {
                    final FormacionAcademica nueva = new FormacionAcademica();
                    nueva.setEmpleadoId(empleado.getId());
                    nueva.setActivo(ACTIVO);
                    nueva.setCreatedAt(LocalDateTime.now());
                    return nueva;
                });

        formacion.setInstitucion(institucion);
        aplicarSiPresente(fila.texto(VinculacionColumna.ESPECIALIDAD_POSGRADO),
                formacion::setCarrera);
        // 'MAESTRIA'/'DOCTORADO' son grados ('Maestro'/'Doctor'), no niveles de instrucción.
        sesion.gradoPosgrado(fila.texto(VinculacionColumna.NIVEL_POSGRADO))
                .id(formacion::setGradoAcademicoId);
        aplicarSiPresente(anioAFecha(fila.numero(VinculacionColumna.FECHA_GRADO)),
                formacion::setFechaFin);
        aplicarCondicionGrado(fila, formacion);

        formacionAcademicaRepository.save(formacion);
    }

    // ------------------------------------------------------------ Cobertura salud
    /**
     * Registra la cobertura de salud (pantalla "Cobertura de salud" de Config. Remunerativa,
     * tabla {@code INDECI_EMPLEADO_SALUD_EPS}) cuando el Excel trae la fecha de inicio de
     * vigencia. El <b>tipo de cobertura se deriva</b> de "¿Afiliado a EPS?": N → ESSALUD
     * (Solo EsSalud 9%), S → ESSALUD_EPS (EsSalud + EPS 6.75% + 2.25%).
     *
     * <p>Sin fecha de inicio no se crea el registro (la columna FECHA_INICIO es NOT NULL);
     * RR.HH. la completa a mano en esa pantalla. La EPS concreta, si aplica, también se
     * registra a mano — el import solo fija el tipo y la vigencia.
     */
    private void upsertSaludEps(VinculacionRowRaw fila, Empleado empleado) {
        final LocalDate fechaInicio = fila.fecha(VinculacionColumna.ESSALUD_FECHA_INICIO);
        if (fechaInicio == null) {
            return;
        }
        final EmpleadoSaludEps cobertura = empleadoSaludEpsRepository
                .findFirstByEmpleadoIdAndEstadoOrderByFechaInicioDesc(empleado.getId(), "ACTIVO")
                .orElseGet(() -> {
                    final EmpleadoSaludEps nueva = new EmpleadoSaludEps();
                    nueva.setEmpleadoId(empleado.getId());
                    nueva.setEstado("ACTIVO");
                    nueva.setCreadoPor(ORIGEN);
                    nueva.setCreadoEn(LocalDateTime.now());
                    return nueva;
                });

        final boolean tieneEps = Boolean.TRUE.equals(fila.logico(VinculacionColumna.TIENE_EPS));
        cobertura.setTipoCobertura(tieneEps ? "ESSALUD_EPS" : "ESSALUD");
        cobertura.setFechaInicio(fechaInicio);
        cobertura.setModificadoPor(ORIGEN);
        cobertura.setModificadoEn(LocalDateTime.now());
        empleadoSaludEpsRepository.save(cobertura);
    }

    /** 'CONDICION/GRADO' es texto; el modelo lo guarda como flags independientes. */
    private void aplicarCondicionGrado(VinculacionRowRaw fila, FormacionAcademica formacion) {
        final String condicion = diccionario.canonico(
                DiccionarioEquivalencias.Catalogo.CONDICION_GRADO,
                fila.texto(VinculacionColumna.CONDICION_GRADO));
        if (condicion == null) {
            return;
        }
        formacion.setEgresado("EGRESADO".equals(condicion) ? 1 : 0);
        formacion.setTitulado("TITULADO".equals(condicion) ? 1 : 0);
    }

    /** El Excel guarda el año del grado (p. ej. 2014), no una fecha completa. */
    private LocalDate anioAFecha(BigDecimal anio) {
        if (anio == null) {
            return null;
        }
        final int valor = anio.intValue();
        return valor >= 1900 && valor <= 2100 ? LocalDate.of(valor, 12, 31) : null;
    }

    private <T> void aplicarSiPresente(T valor, java.util.function.Consumer<T> setter) {
        if (valor != null) {
            setter.accept(valor);
        }
    }
}
