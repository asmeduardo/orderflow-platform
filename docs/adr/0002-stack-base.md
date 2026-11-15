# ADR 0002 — Adotar Java 25, Spring Boot 4.1 e Maven

## Estado

Aceita.

## Contexto

O README previa Java 21 e Spring Boot 3. Em novembro de 2026, a linha estável mais recente do Spring Boot é a 4.1 (a 4.2 ainda está em release candidate) e o Java 25 é a versão LTS atual. A escolha precisa ser suportada por todo o conjunto usado nas fases 1 a 3: Spring MVC, Spring Data JPA/Hibernate, Spring Security, Spring Kafka, Flyway, springdoc-openapi e Testcontainers.

## Decisão

- **Java 25 (LTS)** como versão de compilação (`maven.compiler.release=25`) e de execução no CI.
- **Spring Boot 4.1.x** como base, com as versões gerenciadas pelo BOM do Boot: Spring Framework 7, Hibernate 7.4, Spring Security 7.1, Spring Kafka 4.1, Flyway 12, Jackson 3, JUnit 6 (Jupiter) e Testcontainers 2.
- **springdoc-openapi 3.2**, a primeira versão compilada contra o Spring Boot 4.1.2.
- **Maven** com wrapper versionado (`mvnw`), distribuição fixada e verificada por SHA-256.
- Testes unitários (`*Test`) rodam no Surefire; testes de integração (`*IT`) rodam no Failsafe contra PostgreSQL real via Testcontainers. O agente do Mockito é carregado explicitamente na JVM de teste, como exige o JDK 25.

## Consequências

O projeto usa APIs atuais (por exemplo, `MockMvcTester`, `ProblemDetail` nativo e `HttpStatus.UNPROCESSABLE_CONTENT`) sem camadas de compatibilidade. Em troca, parte da documentação disponível na internet ainda descreve o Spring Boot 3, e bibliotecas de terceiros precisam ter versão compatível com o Boot 4 antes de entrar no projeto. A migração para o Boot 4.2 será avaliada quando ela se tornar estável.
