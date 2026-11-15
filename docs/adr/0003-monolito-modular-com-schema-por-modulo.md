# ADR 0003 — Separar pedidos e estoque por pacote, API pública e schema

## Estado

Aceita.

## Contexto

A ADR 0001 prevê começar com um monólito modular e extrair o estoque depois. A extração só será barata se, desde o início, os módulos não compartilharem tabelas nem detalhes internos. Limites apenas de pacote costumam ser violados por consultas SQL e por relacionamentos JPA entre módulos.

## Decisão

- Cada módulo (`orders`, `inventory`) tem as camadas `domain`, `application` e `web` em seu próprio pacote.
- O estoque expõe ao restante da aplicação apenas o pacote `inventory.api` (`StockReservations`, `StockLine`, `ReservationOutcome`, `Shortage`). Entidades, repositórios e serviços internos não são usados por outros módulos.
- Cada módulo possui um schema PostgreSQL (`orders` e `inventory`). Não existem chaves estrangeiras nem consultas entre schemas: a reserva guarda apenas o identificador do pedido, e o item do pedido guarda apenas o SKU.
- O pacote `shared` contém somente infraestrutura transversal (tradução de erros para `ProblemDetail`, paginação e relógio), sem regras de negócio.
- Na fase 1, o pedido chama `StockReservations` de forma síncrona e na mesma transação local, de modo que pedido e reserva são confirmados ou desfeitos juntos.

## Consequências

A extração do estoque poderá mover o schema `inventory` para outro banco sem reescrever consultas. A transação única simplifica a fase 1, mas cria um acoplamento temporal que desaparecerá na fase 3, quando a comunicação passar a ser assíncrona. A ausência de chaves estrangeiras entre módulos transfere para a aplicação a responsabilidade de lidar com referências a pedidos e produtos inexistentes; isso já é necessário em um sistema distribuído. As regras de dependência entre pacotes serão verificadas por testes de arquitetura na fase 2.
