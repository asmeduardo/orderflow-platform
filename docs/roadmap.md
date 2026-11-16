# Roadmap

## Fase 1 — fundação

- [x] criar o monólito modular com Java 25 e Spring Boot ([ADR 0002](adr/0002-stack-base.md));
- [x] modelar pedido, item, produto e reserva;
- [x] adicionar PostgreSQL e Flyway;
- [x] criar API REST com validação e `ProblemDetail`;
- [x] escrever testes unitários e de integração com Testcontainers;
- [x] publicar OpenAPI, Swagger UI e coleção de exemplos.

## Fase 2 — segurança e qualidade

- [x] integrar OAuth 2.0/OIDC ([ADR 0006](adr/0006-seguranca-oauth2-resource-server.md));
- [x] definir permissões de leitura e operação;
- [x] adicionar testes de autorização;
- [x] aplicar testes de arquitetura;
- [x] configurar análise estática, auditoria de dependências e CI.

## Fase 3 — eventos

- [x] adicionar Kafka local;
- [x] implementar transactional outbox ([ADR 0007](adr/0007-transactional-outbox.md));
- [x] separar o contexto de estoque ([ADR 0009](adr/0009-extracao-do-servico-de-estoque.md));
- [x] definir contratos de evento versionados ([ADR 0008](adr/0008-eventos-versionados-e-consumidores-idempotentes.md));
- [x] implementar idempotência, retry e DLQ;
- [x] testar duplicidade, reordenação e indisponibilidade.

## Fase 4 — operação

- [ ] instrumentar métricas, logs e traces;
- [ ] criar painéis de observabilidade;
- [ ] empacotar os serviços em containers;
- [ ] implantar em `kind` com Kubernetes e Helm;
- [ ] adicionar testes de fumaça após implantação.

## Fase 5 — AWS e demonstração

- [ ] modelar a infraestrutura com Terraform;
- [ ] publicar imagens no ECR;
- [ ] implantar no EKS e usar RDS;
- [ ] configurar logs e alarmes no CloudWatch;
- [ ] documentar custos, limites e destruição da infraestrutura;
- [ ] criar o painel React e gravar a demonstração.
