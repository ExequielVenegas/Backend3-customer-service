package cl.duoc.bancoxyz.customer;
import jakarta.validation.constraints.*;
public record CustomerInput(
 @NotBlank @Size(max=150) String nombre,
 @Size(max=40) String documento,
 @Email @Size(max=254) String email,
 @Size(max=30) String telefono,
 @Size(max=250) String direccion) {}
