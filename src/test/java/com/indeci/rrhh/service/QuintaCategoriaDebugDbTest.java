package com.indeci.rrhh.service;

import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.List;

@SpringBootTest
public class QuintaCategoriaDebugDbTest {

    @Autowired
    private EmpleadoPlanillaRepository empleadoPlanillaRepository;

    @Test
    public void debugSueldos() {
        System.out.println("========== INICIO DEBUG ==========");
        List<EmpleadoPlanilla> activas = empleadoPlanillaRepository.findByActivo(1);
        System.out.println("Encontradas " + activas.size() + " planillas activas.");
        for (EmpleadoPlanilla p : activas) {
            System.out.println("EmpleadoId: " + p.getEmpleadoId() + 
                               ", SueldoBasico: " + p.getSueldoBasico());
        }
        System.out.println("=========== FIN DEBUG  ===========");
    }
}
