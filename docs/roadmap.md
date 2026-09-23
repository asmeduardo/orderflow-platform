# Roadmap

## Fase 1 — fundação

- [ ] criar o monólito modular com Java 21 e Spring Boot;
- [ ] modelar pedido, item, produto e reserva;
- [ ] adicionar PostgreSQL e Flyway;
- [ ] criar API REST com validação e `ProblemDetail`;
- [ ] escrever testes unitários e de integração com Testcontainers;
- [ ] publicar OpenAPI, Swagger UI e coleção de exemplos.

## Fase 2 — segurança e qualidade

- [ ] integrar OAuth 2.0/OIDC;
- [ ] definir permissões de leitura e operação;
- [ ] adicionar testes de autorização;
- [ ] aplicar testes de arquitetura;
- [ ] configurar análise estática, auditoria de dependências e CI.

## Fase 3 — eventos

- [ ] adicionar Kafka local;
- [ ] implementar transactional outbox;
- [ ] separar o contexto de estoque;
- [ ] definir contratos de evento versionados;
- [ ] implementar idempotência, retry e DLQ;
- [ ] testar duplicidade, reordenação e indisponibilidade.

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
