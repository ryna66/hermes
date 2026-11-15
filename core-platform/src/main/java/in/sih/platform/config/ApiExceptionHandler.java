package in.sih.platform.config;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.OptimisticLockingFailureException;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(Exception.class)
    ResponseEntity<java.util.Map<String,Object>> error(Exception ex,HttpServletRequest request) {
        int status=500;String message="Internal server error";
        if(ex instanceof ResponseStatusException e){status=e.getStatusCode().value();message=e.getReason();}
        else if(ex instanceof org.springframework.security.access.AccessDeniedException){status=403;message="Forbidden";}
        else if(ex instanceof OptimisticLockingFailureException){status=409;message="Challenge changed concurrently; reload and retry";}
        else if(ex instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException || ex instanceof org.springframework.http.converter.HttpMessageNotReadableException){status=400;message="Invalid request";}
        else if(ex instanceof org.springframework.web.ErrorResponse e){status=e.getStatusCode().value();message=HttpStatus.valueOf(status).getReasonPhrase();}
        var body=ApiErrors.body(status,message,request.getRequestURI());
        if(ex instanceof MethodArgumentNotValidException e)body.put("fieldErrors",e.getBindingResult().getFieldErrors().stream().map(f->java.util.Map.of("field",f.getField(),"message",f.getDefaultMessage())).toList());
        return ResponseEntity.status(status).body(body);
    }
}
