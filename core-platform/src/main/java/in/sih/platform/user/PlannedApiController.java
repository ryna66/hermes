package in.sih.platform.user;

import in.sih.platform.challenge.AssignmentResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class PlannedApiController {
    private final PlannedApiService service;
    public PlannedApiController(PlannedApiService service){this.service=service;}
    private UUID id(Jwt jwt){return UUID.fromString(jwt.getSubject());}
    @GetMapping("/api/users/me/challenges") @PreAuthorize("hasRole('CITIZEN')")
    public Page<PlannedApiService.OwnChallenge> own(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.own(id(jwt),page,size);}
    public record Audio(@NotBlank @Size(max=1398104) String audioBase64,@NotNull @Pattern(regexp="audio/[A-Za-z0-9.+-]+") String mimeType,@NotBlank @Size(min=2,max=20) String language) { }
    @PostMapping("/api/challenges/transcription") @PreAuthorize("hasRole('CITIZEN')")
    public PlannedApiService.Transcript transcribe(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Audio input){return service.transcribe(id(jwt),input);}
    @GetMapping("/api/organizations/{id}/departments")
    public Map<String,List<PlannedApiService.Faculty>> departments(@PathVariable UUID id){return service.departments(id);}
    public record FacultyInput(UUID id,@NotBlank @Size(max=200) String name,@NotNull Set<@NotBlank @Size(max=200) String> expertiseTags,@NotNull @Size(max=10000) String bio,@Email @Size(max=255) String email) { }
    @PutMapping("/api/organizations/me/departments/{department}/faculty") @PreAuthorize("hasRole('UNIVERSITY')")
    public List<PlannedApiService.Faculty> replace(@AuthenticationPrincipal Jwt jwt,@PathVariable String department,@Valid @RequestBody List<@NotNull @Valid FacultyInput> input){return service.replace(id(jwt),department,input);}
    @GetMapping("/api/university/inbox") @PreAuthorize("hasRole('UNIVERSITY')")
    public Page<PlannedApiService.Inbox> inbox(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) AssignmentResponse assignmentResponse,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.inbox(id(jwt),assignmentResponse,page,size);}
    public record AccountInput(@NotBlank @Size(max=200) String name,@NotNull @Pattern(regexp="\\+?[0-9]{10,15}") String phone,@NotNull Role role,UUID organizationId) { }
    @PostMapping("/api/users") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public CurrentUserController.Account provision(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody AccountInput input){return service.provision(id(jwt),input);}
}
