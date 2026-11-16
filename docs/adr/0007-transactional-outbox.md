# ADR 0007 — Publicar eventos com transactional outbox

## Estado

Aceita.

## Contexto

Quando um pedido é criado ou cancelado, outro contexto precisa saber. Gravar no PostgreSQL e publicar no Kafka na mesma requisição cria uma escrita dupla: se o processo cair entre as duas operações, ou se o broker estiver fora do ar, o banco e os eventos divergem (pedido sem evento ou evento sem pedido). Transações distribuídas (XA) entre PostgreSQL e Kafka não estão disponíveis na prática e acoplariam a disponibilidade da API à do broker.

## Decisão

- Cada contexto tem uma tabela de outbox no próprio schema (`orders.outbox_event`, `inventory.outbox_event`). O evento é inserido, como JSONB, na mesma transação local que altera o agregado; o `TransactionalOutbox` recusa ser chamado fora de uma transação.
- Um relay agendado (`OutboxRelay`, a cada 500 ms por padrão) lê as linhas pendentes em ordem de inserção com `SELECT ... FOR UPDATE SKIP LOCKED`, publica cada uma no Kafka com a chave do pedido e os cabeçalhos `event-id`, `event-type` e `event-version`, e só marca `published_at` depois do acknowledgement do broker (`acks=all`, produtor idempotente).
- Se um envio falha, a linha continua pendente com `attempts` e `last_error` atualizados, e o lote para: eventos posteriores do mesmo pedido nunca ultrapassam um evento que falhou. O intervalo de polling é o atraso entre tentativas.
- O relay espera o acknowledgement por mais tempo que o `delivery.timeout.ms` do produtor, para não desistir de um envio que o próprio produtor ainda está tentando.
- A entrega é *at least once*: um envio que expirou do lado do cliente pode ter chegado ao broker e será enviado de novo. Todas as cópias carregam o mesmo `eventId`, que os consumidores usam para descartar duplicatas (ADR 0008).

## Consequências

A API continua aceitando pedidos com o Kafka fora do ar; os eventos ficam pendentes e saem quando o broker volta, o que `OutboxRelayIT` comprova pausando o container do Kafka. O teste também registrou, na prática, uma entrega duplicada causada por timeout, que é a razão da deduplicação nos consumidores. O custo é uma latência de até um intervalo de polling e linhas publicadas que se acumulam; uma rotina de limpeza das linhas antigas será necessária quando o volume justificar. O bloqueio do lote em caso de falha é intencional: ordem por pedido vale mais que vazão neste domínio.
