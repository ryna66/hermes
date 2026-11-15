package in.sih.platform.config;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record Error(String message) { }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Error> status(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(new Error(error.getReason()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Error> validation(MethodArgumentNotValidException error) {
        String message = error.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage()).collect(java.util.stream.Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new Error(message));
    }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Error> concurrentChange(OptimisticLockingFailureException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Error("Challenge changed concurrently; reload and retry"));
    }
}
