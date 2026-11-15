package in.sih.platform.config;
import java.util.*;
import java.time.Instant;
import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
public final class ApiErrors {
    private ApiErrors() {}
    public static Map<String,Object> body(int status,String message,String path) {
        Map<String,Object> body=new LinkedHashMap<>();body.put("timestamp",Instant.now().toString());body.put("status",status);
        HttpStatus value=HttpStatus.resolve(status);body.put("code",value==null?"ERROR":value.name());body.put("message",message);body.put("path",path);return body;
    }
    public static void write(HttpServletRequest req,HttpServletResponse res,int status) throws java.io.IOException {
        res.setStatus(status);res.setContentType("application/json");new com.fasterxml.jackson.databind.ObjectMapper().writeValue(res.getOutputStream(),body(status,HttpStatus.valueOf(status).getReasonPhrase(),req.getRequestURI()));
    }
}
