package com.indeci.rrhh.vinculacion.importacion;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.Cargo;
import com.indeci.rrhh.entity.EstadoCivil;
import com.indeci.rrhh.entity.GradoAcademico;
import com.indeci.rrhh.entity.Nivel;
import com.indeci.rrhh.entity.NivelInstruccion;
import com.indeci.rrhh.entity.Oficina;
import com.indeci.rrhh.entity.Profesion;
import com.indeci.rrhh.entity.Sede;
import com.indeci.rrhh.repository.CargoRepository;
import com.indeci.rrhh.repository.EstadoCivilRepository;
import com.indeci.rrhh.repository.GradoAcademicoRepository;
import com.indeci.rrhh.repository.NivelInstruccionRepository;
import com.indeci.rrhh.repository.NivelRepository;
import com.indeci.rrhh.repository.OficinaRepository;
import com.indeci.rrhh.repository.ProfesionRepository;
import com.indeci.rrhh.repository.SedeRepository;
import com.indeci.rrhh.repository.TipoCargoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Da de alta las entradas de catálogos <b>abiertos</b> que aparecen en el Excel y todavía
 * no existen (profesión, cargo, sede, oficina, nivel, grado…).
 *
 * <p><b>Cada alta se confirma en su propia transacción</b> ({@link Propagation#REQUIRES_NEW}).
 * Es clave: el import procesa cada fila en una transacción independiente y, si esa fila falla
 * al persistir, se revierte. Si el catálogo se hubiera creado dentro de esa misma transacción,
 * también se revertiría, pero el índice en memoria del resolver seguiría apuntando a su id →
 * las siguientes filas romperían con FK a un catálogo inexistente (ORA-02291). Al confirmar el
 * alta por separado, el catálogo persiste pase lo que pase con la fila.
 */
@Service
@RequiredArgsConstructor
public class CatalogoAltaService {

    private final ProfesionRepository profesionRepository;
    private final CargoRepository cargoRepository;
    private final SedeRepository sedeRepository;
    private final OficinaRepository oficinaRepository;
    private final NivelRepository nivelRepository;
    private final NivelInstruccionRepository nivelInstruccionRepository;
    private final GradoAcademicoRepository gradoAcademicoRepository;
    private final EstadoCivilRepository estadoCivilRepository;
    private final TipoCargoRepository tipoCargoRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long crearProfesion(String nombre) {
        final Profesion p = new Profesion();
        p.setNombre(nombre);
        p.setActivo(1);
        return profesionRepository.save(p).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long crearCargo(String nombre) {
        final Cargo c = new Cargo();
        c.setNombre(nombre);
        c.setActivo(1);
        c.setTipoCargoId(tipoCargoPorDefecto());
        c.setCreatedAt(LocalDateTime.now());
        return cargoRepository.save(c).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long crearSede(String nombre) {
        final Sede s = new Sede();
        s.setNombre(nombre);
        s.setActivo(1);
        return sedeRepository.save(s).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long crearOficina(String nombre) {
        final Oficina o = new Oficina();
        o.setNombre(nombre);
        o.setActivo(1);
        return oficinaRepository.save(o).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long crearNivel(String nombre) {
        final Nivel n = new Nivel();
        n.setNombre(nombre);
        return nivelRepository.save(n).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long crearNivelInstruccion(String nombre) {
        final NivelInstruccion n = new NivelInstruccion();
        n.setNombre(nombre);
        n.setActivo(1);
        return nivelInstruccionRepository.save(n).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long crearGradoAcademico(String nombre) {
        final GradoAcademico g = new GradoAcademico();
        g.setNombre(nombre);
        g.setActivo(1);
        return gradoAcademicoRepository.save(g).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long crearEstadoCivil(String nombre) {
        final EstadoCivil e = new EstadoCivil();
        e.setCodigo(TextoNormalizador.clave(nombre));
        e.setNombre(nombre);
        return estadoCivilRepository.save(e).getId();
    }

    /** INDECI_CARGO.TIPO_CARGO_ID es NOT NULL: se usa el primer tipo de cargo activo. */
    private Long tipoCargoPorDefecto() {
        return tipoCargoRepository.findByActivoOrderByNombreAsc(1).stream()
                .findFirst()
                .map(com.indeci.rrhh.entity.TipoCargo::getId)
                .orElseThrow(() -> new NegocioException(
                        "No hay tipos de cargo activos; no se puede dar de alta el cargo del Excel."));
    }
}
