package com.indeci.rrhh.vinculacion.importacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.indeci.rrhh.entity.Bank;
import com.indeci.rrhh.entity.ModalidadCas;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.entity.RegimenPensionario;
import com.indeci.rrhh.entity.Sexo;
import com.indeci.rrhh.entity.TipoCargo;
import com.indeci.rrhh.dto.IncrementosDsResponseDto;
import com.indeci.rrhh.service.IncrementosDsCalculoService;
import com.indeci.rrhh.repository.BankRepository;
import com.indeci.rrhh.repository.CargoRepository;
import com.indeci.rrhh.repository.DistrictRepository;
import com.indeci.rrhh.repository.EmpleadoBancoRepository;
import com.indeci.rrhh.repository.EmpleadoPensionRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPuestoRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EmpleadoSaludEpsRepository;
import com.indeci.rrhh.repository.EstadoCivilRepository;
import com.indeci.rrhh.repository.FormacionAcademicaRepository;
import com.indeci.rrhh.repository.GradoAcademicoRepository;
import com.indeci.rrhh.repository.ModalidadCasRepository;
import com.indeci.rrhh.repository.NivelInstruccionRepository;
import com.indeci.rrhh.repository.NivelRepository;
import com.indeci.rrhh.repository.OficinaRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.ProfesionRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import com.indeci.rrhh.repository.RegimenPensionarioRepository;
import com.indeci.rrhh.repository.SedeRepository;
import com.indeci.rrhh.repository.SexoRepository;
import com.indeci.rrhh.repository.TipoCargoRepository;
import com.indeci.rrhh.repository.TipoContratoRepository;

/**
 * Prueba de integración del upsert contra H2 (modo Oracle) — el equivalente automático de
 * la carga real: convierte una fila del Excel en registros de las 7 entidades y verifica
 * que el flujo <b>completo</b> queda cableado (resolver de catálogos + persistencia).
 *
 * <p>Cubre lo que el {@code VinculacionImportExcelRealTest} (sin BD) no puede: la
 * resolución del régimen 'CAS' → id de '1057', el alta automática de catálogos abiertos
 * (cargo/sede/oficina) y la <b>idempotencia</b> por DNI + N.° de contrato.
 *
 * <p>Nombrado *IT: excluido de {@code mvn test} por defecto (igual que los otros IT de
 * H2). Ejecutar on-demand: {@code mvnw test -Dtest=VinculacionUpsertIT}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class VinculacionUpsertIT {

    @Autowired private PersonaRepository personaRepository;
    @Autowired private EmpleadoRepository empleadoRepository;
    @Autowired private EmpleadoPlanillaRepository empleadoPlanillaRepository;
    @Autowired private EmpleadoPensionRepository empleadoPensionRepository;
    @Autowired private EmpleadoPuestoRepository empleadoPuestoRepository;
    @Autowired private EmpleadoBancoRepository empleadoBancoRepository;
    @Autowired private FormacionAcademicaRepository formacionAcademicaRepository;
    @Autowired private EmpleadoSaludEpsRepository empleadoSaludEpsRepository;
    @Autowired private ProfesionRepository profesionRepository;
    @Autowired private CargoRepository cargoRepository;
    @Autowired private SedeRepository sedeRepository;
    @Autowired private OficinaRepository oficinaRepository;
    @Autowired private NivelRepository nivelRepository;
    @Autowired private NivelInstruccionRepository nivelInstruccionRepository;
    @Autowired private GradoAcademicoRepository gradoAcademicoRepository;
    @Autowired private EstadoCivilRepository estadoCivilRepository;
    @Autowired private BankRepository bankRepository;
    @Autowired private SexoRepository sexoRepository;
    @Autowired private ModalidadCasRepository modalidadCasRepository;
    @Autowired private TipoContratoRepository tipoContratoRepository;
    @Autowired private RegimenLaboralRepository regimenLaboralRepository;
    @Autowired private RegimenPensionarioRepository regimenPensionarioRepository;
    @Autowired private DistrictRepository districtRepository;
    @Autowired private TipoCargoRepository tipoCargoRepository;

    private VinculacionUpsertService upsertService;
    private CatalogoTextResolver catalogoResolver;

    @BeforeEach
    void setUp() {
        final DiccionarioEquivalencias diccionario = new DiccionarioEquivalencias();
        final PensionExcelParser pensionParser = new PensionExcelParser();

        final CatalogoAltaService altaService = new CatalogoAltaService(
                profesionRepository, cargoRepository, sedeRepository, oficinaRepository,
                nivelRepository, nivelInstruccionRepository, gradoAcademicoRepository,
                estadoCivilRepository, tipoCargoRepository);

        catalogoResolver = new CatalogoTextResolver(
                diccionario, profesionRepository, cargoRepository, sedeRepository,
                oficinaRepository, nivelRepository, nivelInstruccionRepository,
                gradoAcademicoRepository, estadoCivilRepository, bankRepository,
                sexoRepository, modalidadCasRepository, tipoContratoRepository,
                regimenLaboralRepository, regimenPensionarioRepository, districtRepository,
                altaService);

        // El cálculo de incrementos D.S. depende de parámetros remunerativos (pesado de
        // sembrar en H2); se stubbea para devolver remuneración = monto contrato. Aquí solo
        // se verifica que el import CABLEA el sueldoBasico, no la fórmula de incrementos.
        final IncrementosDsCalculoService incrementosDs = mock(IncrementosDsCalculoService.class);
        when(incrementosDs.calcular(any(), any(), any(), any()))
                .thenAnswer(inv -> IncrementosDsResponseDto.sinIncrementos(inv.getArgument(2)));

        upsertService = new VinculacionUpsertService(
                personaRepository, empleadoRepository, empleadoPlanillaRepository,
                empleadoPensionRepository, empleadoPuestoRepository, empleadoBancoRepository,
                formacionAcademicaRepository, empleadoSaludEpsRepository, pensionParser, diccionario,
                incrementosDs);

        seedCatalogos();
    }

    /** Siembra los catálogos CERRADOS que una fila CAS necesita (los abiertos se autocrean). */
    private void seedCatalogos() {
        final RegimenLaboral cas = new RegimenLaboral();
        cas.setCodigo("1057");
        cas.setNombre("CONTRATO ADMINISTRATIVO DE SERVICIOS");
        cas.setActivo(1);
        regimenLaboralRepository.save(cas);

        final ModalidadCas modalidad = new ModalidadCas();
        modalidad.setCodigo("CAS_MOD_TRANS");
        modalidad.setNombre("NECESIDAD TRANSITORIA");
        modalidad.setActivo(1);
        modalidadCasRepository.save(modalidad);

        final Sexo sexo = new Sexo();
        sexo.setId(1L); // Sexo no usa @GeneratedValue: el id se asigna a mano.
        sexo.setCodigo("M");
        sexo.setNombre("MASCULINO");
        sexoRepository.save(sexo);

        final RegimenPensionario onp = new RegimenPensionario();
        onp.setCodigo("ONP");
        onp.setNombre("ONP");
        onp.setTipo("ONP");
        onp.setActivo(1);
        regimenPensionarioRepository.save(onp);

        final Bank banco = new Bank();
        banco.setCode("BN");
        banco.setName("BANCO DE LA NACION");
        banco.setStatus(Bank.STATUS_ACTIVE);
        banco.setCreatedAt(LocalDateTime.now());
        banco.setActivo(1);
        bankRepository.save(banco);

        // INDECI_CARGO.TIPO_CARGO_ID es NOT NULL: el auto-alta de cargo usa un tipo default.
        final TipoCargo tipoCargo = new TipoCargo();
        tipoCargo.setCodigo("GEN");
        tipoCargo.setNombre("GENERICO");
        tipoCargo.setActivo(1);
        tipoCargoRepository.save(tipoCargo);
    }

    /** Fila CAS mínima válida; los pares (columna, valor) permiten variarla. */
    private VinculacionRowRaw filaCas(String dni, String numeroContrato) {
        final VinculacionRowRaw f = new VinculacionRowRaw(3);
        f.put(VinculacionColumna.DNI, dni);
        f.put(VinculacionColumna.NOMBRE_COMPLETO, "QUISPE MAMANI, ROSA");
        f.put(VinculacionColumna.SEXO, "M");
        f.put(VinculacionColumna.CODIGO_AIRHSP, "000102");
        f.put(VinculacionColumna.REGIMEN_LABORAL, "CAS");
        f.put(VinculacionColumna.MODALIDAD_CAS, "NECESIDAD TRANSITORIA");
        f.put(VinculacionColumna.NUMERO_CONTRATO, numeroContrato);
        f.put(VinculacionColumna.MONTO_CONTRATO, "S/. 3,500.00");
        f.put(VinculacionColumna.FECHA_INGRESO, LocalDateTime.of(2020, 1, 2, 0, 0));
        f.put(VinculacionColumna.FECHA_INICIO_CONTRATO, LocalDateTime.of(2020, 1, 2, 0, 0));
        f.put(VinculacionColumna.SISTEMA_PENSIONARIO, "ONP");
        f.put(VinculacionColumna.CARGO, "ANALISTA");
        f.put(VinculacionColumna.NIVEL, "ESPECIALISTA");
        f.put(VinculacionColumna.ESTADO_CIVIL, "SOLTERO");
        f.put(VinculacionColumna.SEDE, "SEDE CENTRAL");
        f.put(VinculacionColumna.OFICINA, "OFICINA GENERAL DE ADMINISTRACION");
        f.put(VinculacionColumna.BANCO, "BN");
        f.put(VinculacionColumna.NUMERO_CUENTA, "04021898649");
        f.put(VinculacionColumna.CCI, "01800000402189864901");
        f.put(VinculacionColumna.TIENE_EPS, "N");
        f.put(VinculacionColumna.ESSALUD_FECHA_INICIO, LocalDateTime.of(2020, 1, 2, 0, 0));
        return f;
    }

    @Test
    @DisplayName("Una fila puebla las 6 entidades y resuelve CAS al régimen 1057")
    void importaFilaCompleta() {
        final CatalogoTextResolver.Sesion sesion = catalogoResolver.nuevaSesion();

        final VinculacionUpsertService.Resultado resultado =
                upsertService.importar(filaCas("24485494", "052-2008"), sesion);

        assertThat(resultado.creado()).isTrue();
        assertThat(resultado.dni()).isEqualTo("24485494");

        final var persona = personaRepository.findByDni("24485494").orElseThrow();
        assertThat(persona.getNombreCompleto()).isEqualTo("QUISPE MAMANI, ROSA");

        final var empleado = empleadoRepository.findByPersonaId(persona.getId()).orElseThrow();
        final var vinculos = empleadoPlanillaRepository.findByEmpleadoId(empleado.getId());
        assertThat(vinculos).hasSize(1);

        // CRÍTICO: 'CAS' del Excel resolvió al id del régimen '1057'.
        final Long idRegimenCas = regimenLaboralRepository.findAll().stream()
                .filter(r -> "1057".equals(r.getCodigo())).findFirst().orElseThrow().getId();
        assertThat(vinculos.get(0).getRegimenLaboralId()).isEqualTo(idRegimenCas);
        // El monto se leyó desde 'S/. 3,500.00'.
        assertThat(vinculos.get(0).getMontoContrato()).isEqualTo(3500.0);
        // El sueldoBasico se calcula y persiste (base del motor / "remuneración vigente").
        // Con el stub sin incrementos, es igual al monto contrato.
        assertThat(vinculos.get(0).getSueldoBasico()).isEqualTo(3500.0);

        // El AIRHSP se escribe en AMBOS campos: la UI de Config. Remunerativa muestra el
        // "Código Plaza AIRHSP" desde registroPlazaAirhsp, no desde codigoAirhsp.
        assertThat(vinculos.get(0).getCodigoAirhsp()).isEqualTo("000102");
        assertThat(vinculos.get(0).getRegistroPlazaAirhsp()).isEqualTo("000102");

        assertThat(empleadoPensionRepository.findByEmpleadoIdAndActivo(empleado.getId(), 1))
                .isNotEmpty();
        assertThat(empleadoPuestoRepository.findFirstByEmpleadoIdAndActivo(empleado.getId(), 1))
                .isPresent();
        assertThat(empleadoBancoRepository.findByEmpleadoId(empleado.getId())).hasSize(1);

        // Cobertura de salud: se crea con la fecha de inicio del Excel y el tipo derivado
        // de "¿Afiliado a EPS?" = N → ESSALUD (Solo EsSalud).
        final var cobertura = empleadoSaludEpsRepository
                .findFirstByEmpleadoIdAndEstadoOrderByFechaInicioDesc(empleado.getId(), "ACTIVO")
                .orElseThrow();
        assertThat(cobertura.getTipoCobertura()).isEqualTo("ESSALUD");
        assertThat(cobertura.getFechaInicio()).isEqualTo(LocalDate.of(2020, 1, 2));
    }

    @Test
    @DisplayName("Los catálogos abiertos se dan de alta solos, incluidos los de id no-IDENTITY")
    void autocreaCatalogosAbiertos() {
        assertThat(cargoRepository.findAll()).isEmpty();

        upsertService.importar(filaCas("24485494", "052-2008"), catalogoResolver.nuevaSesion());

        assertThat(cargoRepository.findAll()).extracting(c -> c.getNombre()).contains("ANALISTA");
        assertThat(sedeRepository.findAll()).extracting(s -> s.getNombre()).contains("SEDE CENTRAL");
        assertThat(oficinaRepository.findAll())
                .extracting(o -> o.getNombre()).contains("OFICINA GENERAL DE ADMINISTRACION");
        // Regresión: Nivel y EstadoCivil no declaraban @GeneratedValue y rompían el alta.
        assertThat(nivelRepository.findAll()).extracting(n -> n.getNombre()).contains("ESPECIALISTA");
        assertThat(estadoCivilRepository.findAll()).isNotEmpty();
    }

    @Test
    @DisplayName("Re-subir la misma fila actualiza, no duplica (idempotencia DNI + N° contrato)")
    void esIdempotente() {
        upsertService.importar(filaCas("24485494", "052-2008"), catalogoResolver.nuevaSesion());
        final VinculacionUpsertService.Resultado segunda =
                upsertService.importar(filaCas("24485494", "052-2008"), catalogoResolver.nuevaSesion());

        assertThat(segunda.creado()).isFalse();
        assertThat(personaRepository.findAll()).hasSize(1);
        assertThat(empleadoRepository.findAll()).hasSize(1);
        assertThat(empleadoPlanillaRepository.findAll()).hasSize(1);
        // El cargo no se duplica al reimportar.
        assertThat(cargoRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Segundo contrato del mismo DNI = vínculo secuencial nuevo, misma persona")
    void segundoContratoEsVinculoNuevo() {
        upsertService.importar(filaCas("24485494", "052-2008"), catalogoResolver.nuevaSesion());
        upsertService.importar(filaCas("24485494", "137-2009"), catalogoResolver.nuevaSesion());

        assertThat(personaRepository.findAll()).hasSize(1);
        final var empleado = empleadoRepository.findAll().get(0);
        assertThat(empleadoPlanillaRepository.findByEmpleadoId(empleado.getId())).hasSize(2);
    }

    @Test
    @DisplayName("Un solo vigente: al importar 2 contratos del mismo DNI, el más antiguo se cierra")
    void reconciliaUnSoloVigente() {
        final CatalogoTextResolver.Sesion sesion = catalogoResolver.nuevaSesion();

        final VinculacionRowRaw antiguo = filaCas("24485494", "052-2008");
        antiguo.put(VinculacionColumna.FECHA_INICIO_CONTRATO, LocalDateTime.of(2020, 1, 2, 0, 0));
        upsertService.importar(antiguo, sesion);

        final VinculacionRowRaw nuevo = filaCas("24485494", "137-2025");
        nuevo.put(VinculacionColumna.FECHA_INICIO_CONTRATO, LocalDateTime.of(2025, 5, 12, 0, 0));
        upsertService.importar(nuevo, sesion);

        final var empleado = empleadoRepository.findAll().get(0);
        final var vinculos = empleadoPlanillaRepository.findByEmpleadoId(empleado.getId());
        assertThat(vinculos).hasSize(2);

        // Solo uno queda vigente (sin cese): el de inicio más reciente (137-2025).
        assertThat(vinculos).filteredOn(v -> v.getFechaCese() == null)
                .singleElement()
                .satisfies(v -> assertThat(v.getNumeroContrato()).isEqualTo("137-2025"));

        // El antiguo se cerró con el sello de transición (día previo al inicio del nuevo, sin doc).
        final var cesado = vinculos.stream()
                .filter(v -> "052-2008".equals(v.getNumeroContrato())).findFirst().orElseThrow();
        assertThat(cesado.getFechaCese()).isEqualTo(LocalDate.of(2025, 5, 11));
        assertThat(cesado.getMotivoCese()).isEqualTo("TRANSICIÓN DE CONTRATO");
        assertThat(cesado.getDocumentoCese()).isNull();
    }

    @Test
    @DisplayName("El 'DNI del jefe inmediato' se resuelve al id del empleado jefe (llena el input)")
    void resuelveJefePorDni() {
        // Primero se importa al jefe; luego al subordinado que lo referencia por DNI.
        upsertService.importar(filaCas("11111111", "JEF-2020"), catalogoResolver.nuevaSesion());
        final Long jefeEmpleadoId = empleadoRepository
                .findByPersonaId(personaRepository.findByDni("11111111").orElseThrow().getId())
                .orElseThrow().getId();

        final VinculacionRowRaw subordinado = filaCas("22222222", "SUB-2020");
        subordinado.put(VinculacionColumna.JEFE_DNI, "11111111");
        upsertService.importar(subordinado, catalogoResolver.nuevaSesion());

        final Long subEmpleadoId = empleadoRepository
                .findByPersonaId(personaRepository.findByDni("22222222").orElseThrow().getId())
                .orElseThrow().getId();
        final var puesto = empleadoPuestoRepository
                .findFirstByEmpleadoIdAndActivo(subEmpleadoId, 1).orElseThrow();
        assertThat(puesto.getJefeId()).isEqualTo(jefeEmpleadoId);
    }

    @Test
    @DisplayName("Jefe con DNI aún no cargado no rompe: queda sin jefe")
    void jefeInexistenteNoRompe() {
        final VinculacionRowRaw fila = filaCas("22222222", "SUB-2020");
        fila.put(VinculacionColumna.JEFE_DNI, "99999999"); // no existe
        upsertService.importar(fila, catalogoResolver.nuevaSesion());

        final Long empId = empleadoRepository
                .findByPersonaId(personaRepository.findByDni("22222222").orElseThrow().getId())
                .orElseThrow().getId();
        assertThat(empleadoPuestoRepository.findFirstByEmpleadoIdAndActivo(empId, 1).orElseThrow()
                .getJefeId()).isNull();
    }
}
