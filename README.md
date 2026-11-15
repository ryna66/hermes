# SIH26043

Milestone 2: Java 21 / Maven 3.9+, Spring Boot 3.3.5 skeleton. AI implementation and authentication arrive in later milestones. Only Swagger and health are publicly accessible; other routes are denied pending authentication implementation.

Run from the repository root:

```sh
mvn -f core-platform/pom.xml clean verify
mvn -f core-platform/pom.xml spring-boot:run
```

Use a Java 21 `JAVA_HOME` (macOS: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`). Default profile is `dev`: Flyway creates all tables, Hibernate validates mappings, then the dev-only seeder logs 10 universities, 30 faculty, 20 challenges, 5 industry organizations, one government organization and five users (one per role). Submission events and notifications accompany seeded challenges. H2 data disappears when the JVM stops.

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health

Repositories use JPA; schema changes belong in new Flyway migrations. Team lead/organization and lifecycle rules will be enforced by application services in their milestones. No automatic lifecycle changes are implemented here.

## Switching to a permanent database

1. Start Docker Desktop (or another Docker daemon).
2. From the repository root, set a local password and start the optional database:

   ```sh
   export DB_PASSWORD='replace-with-a-local-password'
   docker compose --profile database up -d --wait postgres
   ```

3. Stop the dev application, then start against PostgreSQL using these environment variables:

   ```sh
   export SPRING_PROFILES_ACTIVE=prod
   export DB_URL='jdbc:postgresql://localhost:5432/sih'
   export DB_USERNAME=sih
   # Keep DB_PASSWORD from step 2 in this shell.
   mvn -f core-platform/pom.xml spring-boot:run
   ```

   Flyway applies the same V1 SQL automatically; Hibernate validates it. The prod profile never seeds sample data. Check startup for migration success and open `/actuator/health` and Swagger UI. Prod requires all three DB variables, with no credential defaults.

4. Restart the app with the same environment to retain data. `docker compose --profile database down` stops PostgreSQL while preserving its named volume. Do not remove the volume if you need the data.
5. To return to temporary H2, stop the app, run `unset SPRING_PROFILES_ACTIVE DB_URL DB_USERNAME DB_PASSWORD`, then run the default startup command.

The SQL uses UUID, standard text/numeric/boolean/date/timestamp types and foreign keys; no vendor-specific extensions. PostgreSQL execution must be verified with a running daemon; a Docker client alone is insufficient.
