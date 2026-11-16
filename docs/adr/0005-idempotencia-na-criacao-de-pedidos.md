# ADR 0005 — Exigir chave de idempotência na criação de pedidos

## Estado

Aceita.

## Contexto

Canais de venda repetem requisições quando há timeout ou queda de conexão. Sem proteção, cada repetição cria outro pedido e reserva estoque novamente. A especificação em elaboração no IETF (`Idempotency-Key` HTTP header field) descreve o padrão adotado por APIs de pagamento: o cliente envia uma chave única por tentativa de operação.

## Decisão

- `POST /api/v1/orders` exige o cabeçalho `Idempotency-Key` (8 a 100 caracteres: letras, dígitos, `.`, `_`, `:` ou `-`).
- A tabela `orders.idempotency_key` guarda a chave, o pedido criado e o SHA-256 de uma forma canônica da requisição. A ordem dos itens e zeros à direita nos preços não alteram essa impressão digital.
- Mesma chave e mesma requisição devolvem o pedido original com o mesmo status `201`, o mesmo `Location` e o cabeçalho `Idempotent-Replayed: true`, sem nova reserva.
- Mesma chave com requisição diferente responde `422` com o tipo `urn:orderflow:problem:idempotency-key-reused`.
- A linha da chave é gravada e enviada ao banco antes da reserva de estoque. Uma repetição concorrente fica bloqueada na chave primária até a primeira transação terminar, falha por violação de unicidade, tem a transação desfeita e então responde com o pedido vencedor.

## Consequências

`OrderIdempotencyIT` comprova que 12 repetições simultâneas criam exatamente um pedido e uma reserva. As chaves não expiram enquanto o pedido existir; uma política de retenção poderá ser criada se o volume justificar. Na fase 1, as chaves eram globais; desde a fase 2 elas são únicas por cliente autenticado ([ADR 0006](0006-seguranca-oauth2-resource-server.md)).
