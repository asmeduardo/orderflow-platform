# Arquitetura planejada

## Objetivos

O sistema deverá confirmar pedidos sem perder rastreabilidade quando um serviço ficar indisponível, uma mensagem for entregue mais de uma vez ou o processo for interrompido. A arquitetura priorizará consistência observável, recuperação e simplicidade operacional.

## Limites de domínio

- **Order:** recebe a intenção de compra, valida a requisição e controla o estado do pedido.
- **Inventory:** mantém a disponibilidade e registra reservas de estoque.
- **Notification:** reage a eventos confirmados e registra a entrega de notificações simuladas.

Cada contexto possuirá dados próprios. A comunicação assíncrona ocorrerá por eventos versionados no Kafka. Consultas síncronas serão usadas somente quando o resultado imediato fizer parte do contrato HTTP.

## Implementação atual

A fase 1 entrega um monólito modular ([ADR 0003](adr/0003-monolito-modular-com-schema-por-modulo.md)):

- `orders` recebe o pedido, valida o canal, a moeda e os itens e controla os estados `PENDING`, `CONFIRMED`, `REJECTED` e `CANCELLED`;
- `inventory` mantém produtos, quantidades em estoque e reservas;
- cada módulo tem um schema PostgreSQL próprio, sem chaves estrangeiras entre eles;
- `orders` usa apenas a API pública `inventory.api` e reserva o estoque de forma síncrona, na mesma transação que grava o pedido;
- a reserva é tudo ou nada e segura sob concorrência ([ADR 0004](adr/0004-reserva-de-estoque-concorrente.md));
- a criação de pedidos é idempotente por `Idempotency-Key` ([ADR 0005](adr/0005-idempotencia-na-criacao-de-pedidos.md));
- valores monetários usam `BigDecimal` na escala da moeda, e datas são instantes UTC com precisão de microssegundos.

Desde a fase 2, a API é um resource server OAuth 2.0: os escopos `orderflow.read` e `orderflow.operate` separam consulta e operação, a chave de idempotência é isolada por cliente e testes ArchUnit impedem dependências entre módulos fora de `inventory.api` ([ADR 0006](adr/0006-seguranca-oauth2-resource-server.md)). Em desenvolvimento, o módulo `dev-auth-server` emite os tokens.

A comunicação por eventos e a extração do estoque estão previstas para a fase 3.

## Decisões de consistência

- O pedido será persistido na mesma transação que registra o evento na tabela de outbox.
- Consumidores guardarão a chave de idempotência antes de aplicar efeitos de domínio.
- A reserva de estoque usará controle de concorrência e restrições no banco.
- Retentativas terão limite, atraso e motivo observável.
- Mensagens sem recuperação automática seguirão para uma dead-letter queue.
- O status final será derivado de evidências persistidas, não apenas da ausência de erro no cliente.

## Implantação

O ambiente local será executado com Docker Compose. O ambiente de demonstração usará Kubernetes; os manifests serão validados primeiro com `kind`. A implantação na AWS será opcional e reproduzível com Terraform, com documentação de custos e procedimento de destruição dos recursos.

Nenhum recurso pago será criado automaticamente pelo CI.

## Segurança

O projeto usará OAuth 2.0/OIDC, autorização por escopo, validação de entrada, segregação de segredos e imagens com usuário sem privilégios. Logs não poderão armazenar tokens nem dados pessoais desnecessários.
