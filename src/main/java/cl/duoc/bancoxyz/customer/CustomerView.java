package cl.duoc.bancoxyz.customer;
import java.util.UUID;
public record CustomerView(UUID clienteId,String nombre,String documento,String email,
 String telefono,String direccion,String origen,long version) {}
