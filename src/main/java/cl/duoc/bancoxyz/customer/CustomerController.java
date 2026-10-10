package cl.duoc.bancoxyz.customer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @Validated @RequestMapping("/internal/customers")
public class CustomerController {
 private final CustomerService service;
 public CustomerController(CustomerService service) { this.service=service; }
 public record Update(@NotNull @Min(0) Long version,@NotNull @Valid CustomerInput perfil) {}
 @PostMapping
 public ResponseEntity<CustomerView> create(@RequestHeader("Idempotency-Key") UUID key,
  @Valid @RequestBody CustomerInput input,@AuthenticationPrincipal Jwt jwt) {
  var customer=service.create(key,jwt.getSubject(),input);
  return ResponseEntity.created(URI.create("/internal/customers/"+customer.clienteId())).body(customer);
 }
 @GetMapping("/{id}") public CustomerView find(@PathVariable UUID id) { return service.find(id); }
 @GetMapping("/{id}/saludo") public Map<String,Object> greeting(@PathVariable UUID id) {
  var customer=service.find(id);
  return Map.of("clienteId",customer.clienteId(),"nombre",customer.nombre());
 }
 @GetMapping("/{id}/existencia") public Map<String,UUID> exists(@PathVariable UUID id) {
  return Map.of("clienteId",service.find(id).clienteId());
 }
 @GetMapping public List<CustomerView> list(@RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,
  @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return service.list(page,size); }
 @PutMapping("/{id}") public CustomerView update(@PathVariable UUID id,@Valid @RequestBody Update update,
  @AuthenticationPrincipal Jwt jwt) { return service.update(id,update.version(),jwt.getSubject(),update.perfil()); }
}
