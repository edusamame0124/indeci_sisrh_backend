package com.indeci.rrhh.vinculacion.importacion;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.indeci.rrhh.entity.Cargo;
import com.indeci.rrhh.entity.EstadoCivil;
import com.indeci.rrhh.entity.GradoAcademico;
import com.indeci.rrhh.entity.Nivel;
import com.indeci.rrhh.entity.NivelInstruccion;
import com.indeci.rrhh.entity.Oficina;
import com.indeci.rrhh.entity.Profesion;
import com.indeci.rrhh.entity.Sede;
import com.indeci.rrhh.repository.BankRepository;
import com.indeci.rrhh.repository.CargoRepository;
import com.indeci.rrhh.repository.DistrictRepository;
import com.indeci.rrhh.repository.EstadoCivilRepository;
import com.indeci.rrhh.repository.GradoAcademicoRepository;
import com.indeci.rrhh.repository.ModalidadCasRepository;
import com.indeci.rrhh.repository.NivelInstruccionRepository;
import com.indeci.rrhh.repository.NivelRepository;
import com.indeci.rrhh.repository.OficinaRepository;
import com.indeci.rrhh.repository.ProfesionRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import com.indeci.rrhh.repository.RegimenPensionarioRepository;
import com.indeci.rrhh.repository.SedeRepository;
import com.indeci.rrhh.repository.SexoRepository;
import com.indeci.rrhh.repository.TipoContratoRepository;
import com.indeci.rrhh.vinculacion.importacion.CatalogoTexto.Politica;
import com.indeci.rrhh.vinculacion.importacion.CatalogoTexto.Resolucion;
import com.indeci.rrhh.vinculacion.importacion.DiccionarioEquivalencias.Catalogo;

/**
 * Traduce el texto del Excel al id de cada catálogo del sistema.
 *
 * <p>Responde a la pregunta "el Excel no trae ids, ¿cómo migro?": el id <b>no</b> tiene que
 * venir en el archivo — se resuelve (o se crea) a partir del nombre.
 *
 * <p>Política por catálogo (decisión RR.HH., ver INFORME_VERIFICACION_EXCEL_OFICIAL.md §7):
 * <ul>
 *   <li><b>ABIERTO</b> (profesión, cargo, sede, oficina, nivel, nivel de instrucción, grado,
 *       estado civil) → alta automática: la cola larga no bloquea la migración.</li>
 *   <li><b>CERRADO</b> (distrito/Ubigeo, régimen laboral, régimen pensionario/AFP, sexo,
 *       modalidad CAS, tipo de contrato, banco) → solo match: inventar un Ubigeo o una AFP
 *       corrompería datos normativos.</li>
 * </ul>
 *
 * <p>Cada catálogo se indexa una sola vez por importación (no una consulta por fila).
 * Instancia por importación: no es un singleton con estado compartido — ver
 * {@link #nuevaSesion()}.
 */
@Component
public class CatalogoTextResolver {

    private final DiccionarioEquivalencias diccionario;
    private final ProfesionRepository profesionRepository;
    private final CargoRepository cargoRepository;
    private final SedeRepository sedeRepository;
    private final OficinaRepository oficinaRepository;
    private final NivelRepository nivelRepository;
    private final NivelInstruccionRepository nivelInstruccionRepository;
    private final GradoAcademicoRepository gradoAcademicoRepository;
    private final EstadoCivilRepository estadoCivilRepository;
    private final BankRepository bankRepository;
    private final SexoRepository sexoRepository;
    private final ModalidadCasRepository modalidadCasRepository;
    private final TipoContratoRepository tipoContratoRepository;
    private final RegimenLaboralRepository regimenLaboralRepository;
    private final RegimenPensionarioRepository regimenPensionarioRepository;
    private final DistrictRepository districtRepository;
    /** Da de alta los catálogos abiertos en transacción propia (evita ids fantasma). */
    private final CatalogoAltaService altaService;

    @SuppressWarnings("java:S107") // un resolver de catálogos necesita, por definición, sus catálogos
    public CatalogoTextResolver(
            DiccionarioEquivalencias diccionario,
            ProfesionRepository profesionRepository,
            CargoRepository cargoRepository,
            SedeRepository sedeRepository,
            OficinaRepository oficinaRepository,
            NivelRepository nivelRepository,
            NivelInstruccionRepository nivelInstruccionRepository,
            GradoAcademicoRepository gradoAcademicoRepository,
            EstadoCivilRepository estadoCivilRepository,
            BankRepository bankRepository,
            SexoRepository sexoRepository,
            ModalidadCasRepository modalidadCasRepository,
            TipoContratoRepository tipoContratoRepository,
            RegimenLaboralRepository regimenLaboralRepository,
            RegimenPensionarioRepository regimenPensionarioRepository,
            DistrictRepository districtRepository,
            CatalogoAltaService altaService) {
        this.diccionario = diccionario;
        this.profesionRepository = profesionRepository;
        this.cargoRepository = cargoRepository;
        this.altaService = altaService;
        this.sedeRepository = sedeRepository;
        this.oficinaRepository = oficinaRepository;
        this.nivelRepository = nivelRepository;
        this.nivelInstruccionRepository = nivelInstruccionRepository;
        this.gradoAcademicoRepository = gradoAcademicoRepository;
        this.estadoCivilRepository = estadoCivilRepository;
        this.bankRepository = bankRepository;
        this.sexoRepository = sexoRepository;
        this.modalidadCasRepository = modalidadCasRepository;
        this.tipoContratoRepository = tipoContratoRepository;
        this.regimenLaboralRepository = regimenLaboralRepository;
        this.regimenPensionarioRepository = regimenPensionarioRepository;
        this.districtRepository = districtRepository;
    }

    /**
     * Índices vivos de una importación. Se crea uno por archivo: mantiene la caché de
     * catálogos acotada a la operación y evita estado compartido entre importaciones.
     */
    public Sesion nuevaSesion() {
        return new Sesion();
    }

    /** Catálogos indexados para una importación concreta. */
    public class Sesion {

        // Catálogos ABIERTOS: se indexan aquí (findAll) pero el ALTA se delega a
        // CatalogoAltaService, que la confirma en transacción propia (evita ids fantasma
        // si la fila que disparó el alta luego se revierte).
        private final CatalogoTexto profesion = abierto("Profesión",
                () -> pares(profesionRepository.findAll(), Profesion::getNombre, Profesion::getId),
                altaService::crearProfesion);

        private final CatalogoTexto cargo = abierto("Cargo",
                () -> pares(cargoRepository.findAll(), Cargo::getNombre, Cargo::getId),
                altaService::crearCargo);

        private final CatalogoTexto sede = abierto("Sede",
                () -> pares(sedeRepository.findAll(), Sede::getNombre, Sede::getId),
                altaService::crearSede);

        private final CatalogoTexto oficina = abierto("Oficina",
                () -> pares(oficinaRepository.findAll(), Oficina::getNombre, Oficina::getId),
                altaService::crearOficina);

        private final CatalogoTexto nivel = abierto("Nivel",
                () -> pares(nivelRepository.findAll(), Nivel::getNombre, Nivel::getId),
                altaService::crearNivel);

        private final CatalogoTexto nivelInstruccion = abierto("Nivel de instrucción",
                () -> pares(nivelInstruccionRepository.findAll(),
                        NivelInstruccion::getNombre, NivelInstruccion::getId),
                altaService::crearNivelInstruccion);

        private final CatalogoTexto gradoAcademico = abierto("Grado académico",
                () -> pares(gradoAcademicoRepository.findAll(),
                        GradoAcademico::getNombre, GradoAcademico::getId),
                altaService::crearGradoAcademico);

        /*
         * Se indexa por CÓDIGO y por NOMBRE. El código es lo que hace match: V012_37 sembró
         * los canónicos con CODIGO='CASADO','SOLTERO'... que es justo lo que devuelve el
         * diccionario. (En BD conviven los legacy '001..006' con nombres inconsistentes
         * —'Divorsiado/a', 'Soltero/a' duplicado—; por eso no se resuelve por nombre.)
         */
        private final CatalogoTexto estadoCivil = abierto("Estado civil",
                () -> estadoCivilRepository.findAll().stream()
                        .flatMap(e -> java.util.stream.Stream.of(
                                new Object[] {e.getCodigo(), e.getId()},
                                new Object[] {e.getNombre(), e.getId()}))
                        .toList(),
                altaService::crearEstadoCivil);

        // ---- Cerrados: solo match. Nunca se inventan.
        private final CatalogoTexto banco = cerrado("Banco",
                () -> bankRepository.findAll().stream()
                        .map(b -> new Object[] {b.getName(), b.getId()}).toList());

        private final CatalogoTexto sexo = cerrado("Sexo",
                () -> sexoRepository.findAll().stream()
                        .flatMap(s -> java.util.stream.Stream.of(
                                new Object[] {s.getCodigo(), s.getId()},
                                new Object[] {s.getNombre(), s.getId()}))
                        .toList());

        private final CatalogoTexto modalidadCas = cerrado("Modalidad CAS",
                () -> modalidadCasRepository.findAll().stream()
                        .map(m -> new Object[] {m.getNombre(), m.getId()}).toList());

        private final CatalogoTexto tipoContrato = cerrado("Tipo de contrato",
                () -> tipoContratoRepository.findAll().stream()
                        .flatMap(t -> java.util.stream.Stream.of(
                                new Object[] {t.getCodigo(), t.getId()},
                                new Object[] {t.getNombre(), t.getId()}))
                        .toList());

        private final CatalogoTexto regimenLaboral = cerrado("Régimen laboral",
                () -> regimenLaboralRepository.findAll().stream()
                        .flatMap(r -> java.util.stream.Stream.of(
                                new Object[] {r.getCodigo(), r.getId()},
                                new Object[] {r.getNombre(), r.getId()}))
                        .toList());

        private final CatalogoTexto regimenPensionario = cerrado("Régimen pensionario",
                () -> regimenPensionarioRepository.findAll().stream()
                        .flatMap(r -> java.util.stream.Stream.of(
                                new Object[] {r.getCodigo(), r.getId()},
                                new Object[] {r.getNombre(), r.getId()}))
                        .toList());

        public Resolucion profesion(String texto) {
            return profesion.resolver(texto);
        }

        public Resolucion cargo(String texto) {
            return cargo.resolver(texto);
        }

        public Resolucion sede(String texto) {
            return sede.resolver(texto);
        }

        public Resolucion oficina(String texto) {
            return oficina.resolver(texto);
        }

        public Resolucion nivel(String texto) {
            return nivel.resolver(texto);
        }

        public Resolucion nivelInstruccion(String texto) {
            return nivelInstruccion.resolver(
                    diccionario.canonico(Catalogo.NIVEL_INSTRUCCION, texto));
        }

        public Resolucion gradoAcademico(String texto) {
            return gradoAcademico.resolver(
                    diccionario.canonico(Catalogo.GRADO_ACADEMICO, texto));
        }

        /**
         * El nivel de posgrado del Excel ('MAESTRIA', 'MAGISTER', 'DOCTORADO') se guarda como
         * grado académico: el catálogo real ya tiene 'Maestro' y 'Doctor'.
         */
        public Resolucion gradoPosgrado(String texto) {
            return gradoAcademico.resolver(
                    diccionario.canonico(Catalogo.NIVEL_POSGRADO, texto));
        }

        public Resolucion estadoCivil(String texto) {
            return estadoCivil.resolver(diccionario.canonico(Catalogo.ESTADO_CIVIL, texto));
        }

        public Resolucion banco(String texto) {
            return banco.resolver(diccionario.canonico(Catalogo.BANCO, texto));
        }

        public Resolucion sexo(String texto) {
            return sexo.resolver(texto);
        }

        public Resolucion modalidadCas(String texto) {
            return modalidadCas.resolver(texto);
        }

        public Resolucion tipoContrato(String texto) {
            return tipoContrato.resolver(texto);
        }

        /**
         * Resuelve el régimen aplicando primero el alias: el Excel dice 'CAS' pero el
         * catálogo lo registra como '1057', y 'LEY 30057'/'SERVIR' como '30057'.
         */
        public Resolucion regimenLaboral(String texto) {
            return regimenLaboral.resolver(
                    diccionario.canonico(Catalogo.REGIMEN_LABORAL, texto));
        }

        /** Para AFP y ONP/CPMP: el sistema los guarda en el mismo catálogo. */
        public Resolucion regimenPensionario(String texto) {
            return regimenPensionario.resolver(texto);
        }

        /**
         * Distrito (Ubigeo). Catálogo <b>cerrado</b> y con id de tipo texto, por eso no usa
         * {@link CatalogoTexto}. Si el nombre no existe se devuelve vacío: jamás se crea.
         */
        public Optional<String> distrito(String texto) {
            final String clave = TextoNormalizador.clave(texto);
            if (clave == null) {
                return Optional.empty();
            }
            return districtRepository.findAll().stream()
                    .filter(d -> clave.equals(TextoNormalizador.clave(d.getName())))
                    .map(com.indeci.rrhh.entity.District::getId)
                    .findFirst();
        }
    }

    private CatalogoTexto abierto(String nombre,
            java.util.function.Supplier<List<Object[]>> cargar,
            java.util.function.Function<String, Long> alta) {
        return new CatalogoTexto(nombre, Politica.ABIERTO, cargar, alta);
    }

    private CatalogoTexto cerrado(String nombre,
            java.util.function.Supplier<List<Object[]>> cargar) {
        return new CatalogoTexto(nombre, Politica.CERRADO, cargar, null);
    }

    private <T> List<Object[]> pares(List<T> entidades,
            java.util.function.Function<T, String> nombre,
            java.util.function.Function<T, Long> id) {
        return entidades.stream()
                .map(e -> new Object[] {nombre.apply(e), id.apply(e)})
                .toList();
    }
}
