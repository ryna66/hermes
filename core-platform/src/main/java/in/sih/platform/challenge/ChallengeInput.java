package in.sih.platform.challenge;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;

public record ChallengeInput(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String description,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotBlank @Size(max = 255) String district,
        Category category,
        @JsonAlias("affected_people") @Min(1) Integer affectedPeople) { }
