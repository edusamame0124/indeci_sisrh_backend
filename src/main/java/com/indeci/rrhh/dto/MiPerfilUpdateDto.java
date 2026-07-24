package com.indeci.rrhh.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MiPerfilUpdateDto {

    @Pattern(
        regexp = "^$|^[0-9]{7,15}$",
        message = "El teléfono debe contener entre 7 y 15 números"
    )
    private String telefono;

    @Email(message = "El correo personal no tiene un formato válido")
    @Size(max = 150, message = "El correo personal no debe superar los 150 caracteres")
    private String correoPersonal;

    @Size(max = 300, message = "La dirección no debe superar los 300 caracteres")
    private String direccion;

    @Size(
        max = 150,
        message = "El nombre del contacto de emergencia no debe superar los 150 caracteres"
    )
    private String contactoEmergenciaNombre;

    @Size(
        max = 50,
        message = "El parentesco no debe superar los 50 caracteres"
    )
    private String contactoEmergenciaParentesco;

    @Pattern(
        regexp = "^$|^[0-9]{7,15}$",
        message = "El teléfono de emergencia debe contener entre 7 y 15 números"
    )
    private String contactoEmergenciaTelefono;
}