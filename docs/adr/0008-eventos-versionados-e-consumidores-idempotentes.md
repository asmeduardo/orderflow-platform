# ADR 0008 — Reservar estoque por eventos versionados com consumidores idempotentes

## Estado

Aceita. Substitui a reserva síncrona descrita na ADR 0003.

## Contexto

Na fase 1, o módulo de pedidos chamava o estoque na mesma transação. Para extrair o estoque (ADR 0001), a comunicação precisa funcionar entre processos e bancos diferentes, tolerando entregas repetidas, mensagens inválidas, falhas temporárias e eventos fora de ordem.

## Decisão

- **Fluxo:** o pedido nasce `PENDING` e publica `OrderPlaced`. O estoque reserva tudo ou nada e publica `StockReserved` ou `StockRejected`, com as faltas; o pedido passa a `CONFIRMED` ou `REJECTED`. O cancelamento publica `OrderCancelled`, e o estoque libera a reserva.
- **Contratos versionados:** o módulo `event-contracts` define um envelope (`eventId`, `eventType`, `eventVersion`, `occurredAt`, `aggregateId`, `payload`) e um record por versão de payload (`OrderPlacedV1` etc.). Arquivos JSON de referência em `event-contracts/src/test/resources/contracts` são o contrato publicado; `EventContractTest` falha se a serialização mudar. Leitores ignoram campos desconhecidos, então acrescentar campos é compatível; mudanças incompatíveis geram uma nova versão do evento.
- **Tópicos e ordem:** `orderflow.order-events` e `orderflow.inventory-events`, com três partições e chave igual ao id do pedido, o que mantém a ordem dos eventos de um pedido.
- **Idempotência:** cada consumidor grava o `eventId` em `processed_event` antes de aplicar os efeitos, na mesma transação. Uma entrega repetida encontra o registro e não faz nada; uma falha desfaz o registro junto com os efeitos.
- **Falhas:** o `DefaultErrorHandler` repete o registro três vezes com backoff exponencial (500 ms, multiplicador 2, máximo 5 s), bloqueando a partição para preservar a ordem. Depois disso, ou imediatamente para erros que uma nova tentativa não resolve (JSON inválido, versão de evento não suportada), o registro é copiado para `<tópico>.DLT` com a exceção nos cabeçalhos e o consumo segue.
- **Fora de ordem:** um `OrderPlaced` que chega depois do `OrderCancelled` (por exemplo, reprocessado da DLT) não reserva nada, porque o cancelamento deixa um registro em `inventory.cancelled_order`. Do lado dos pedidos, um resultado de reserva só altera pedidos `PENDING`; um pedido cancelado continua cancelado.

## Consequências

A criação de pedidos não depende mais da disponibilidade do estoque, e o status final passa a ser eventual: o cliente consulta `GET /api/v1/orders/{id}`. Os testes cobrem entrega duplicada, mensagem inválida, versão não suportada, falha temporária com backoff crescente, falha persistente terminando na DLT e eventos fora de ordem. Retentativas não bloqueantes (tópicos de retry) foram descartadas porque quebrariam a ordem por pedido. Reprocessar a DLT ainda é uma operação manual; uma ferramenta para isso faz parte do painel operacional planejado.
