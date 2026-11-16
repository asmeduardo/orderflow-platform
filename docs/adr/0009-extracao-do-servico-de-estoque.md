# ADR 0009 — Extrair o estoque para um serviço com banco próprio

## Estado

Aceita.

## Contexto

Com a reserva feita por eventos (ADR 0008), o módulo de estoque já não tinha dependências de código nem de tabelas do módulo de pedidos, o que os testes ArchUnit verificavam. O passo previsto na ADR 0001 é separar o estoque em um processo e um banco próprios, para que cada contexto seja implantado, escalado e mantido de forma independente.

## Decisão

- **Serviços:** `order-service` (porta 8080) mantém o histórico Flyway do monólito e o schema `orders`. `inventory-service` (porta 8081) é uma nova aplicação Spring Boot com o módulo de estoque e o próprio PostgreSQL (banco `inventory`, schema `inventory`).
- **Código compartilhado:** só infraestrutura, em `service-support` (tratamento de erros, segurança, OpenAPI, outbox, relay, deduplicação e política de retry) e `service-test-support` (Testcontainers, chaves JWT de teste e utilitários de Kafka). Os contextos compartilham apenas `event-contracts`; nenhum depende do código do outro.
- **Migrações:** o `inventory-service` começa com uma migração `V1` que consolida as tabelas de estoque do monólito, com a mesma estrutura. O `order-service` recebe a migração `V10`, que remove o schema `inventory` do banco de pedidos.
- **Testes:** cada serviço é testado isoladamente contra PostgreSQL e Kafka reais, publicando os eventos do outro lado exatamente como os contratos definem. Os testes de contrato garantem que os dois lados leem e escrevem o mesmo JSON.

## Procedimento de corte (cut-over)

Para um ambiente que já tenha dados do monólito:

1. subir o banco do estoque e o `inventory-service` (a `V1` cria as tabelas vazias);
2. pausar a criação de pedidos e esperar o outbox do estoque esvaziar (`published_at` preenchido em todas as linhas);
3. copiar os dados do schema `inventory` (por exemplo, `pg_dump --data-only --schema=inventory` do banco antigo, restaurado no novo);
4. publicar o `order-service` com a `V10`, que remove o schema antigo;
5. retomar o tráfego.

Rollback: até o passo 4, basta voltar à versão anterior; depois dele, os dados do estoque existem apenas no banco novo.

## Consequências

Cada serviço tem seu próprio ciclo de vida e seu próprio banco, e uma falha no estoque não impede a criação de pedidos. Em troca, o ambiente local passa a ter três processos Java e dois bancos, não há mais teste automatizado que suba os dois serviços juntos (a integração depende dos testes de contrato) e a `V10` é destrutiva por natureza, por isso exige o procedimento acima.
