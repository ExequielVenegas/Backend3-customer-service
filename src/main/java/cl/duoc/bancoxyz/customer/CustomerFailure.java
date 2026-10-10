package cl.duoc.bancoxyz.customer;
import org.springframework.http.HttpStatus;
public class CustomerFailure extends RuntimeException {
 final HttpStatus status;
 public CustomerFailure(HttpStatus status,String message) { super(message); this.status=status; }
}
