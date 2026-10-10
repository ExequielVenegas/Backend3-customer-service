package cl.duoc.bancoxyz.customer;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
@RestControllerAdvice
public class CustomerErrors {
 @ExceptionHandler(CustomerFailure.class) ResponseEntity<?> business(CustomerFailure e) {
  return ResponseEntity.status(e.status).body(Map.of("status",e.status.value(),"message",e.getMessage()));
 }
 @ExceptionHandler({MethodArgumentNotValidException.class,ConstraintViolationException.class,MissingRequestHeaderException.class,
   MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class})
 ResponseEntity<?> badRequest(Exception e) { return ResponseEntity.badRequest().body(Map.of("status",400,"message","Solicitud invalida")); }
}
