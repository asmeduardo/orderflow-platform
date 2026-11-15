# ADR 0004 — Reservar estoque com bloqueio ordenado e restrições no banco

## Estado

Aceita.

## Contexto

O problema central do produto é não vender a mesma unidade duas vezes. Pedidos de canais diferentes podem disputar a última unidade ao mesmo tempo, e um pedido pode ter vários itens que precisam ser reservados juntos (tudo ou nada). Uma primeira implementação que lia o produto, verificava a disponibilidade e gravava a nova quantidade falhou no teste de concorrência: com `@Version`, as requisições concorrentes recebiam erro de bloqueio otimista em vez de um resultado determinístico; sem `@Version`, o estoque seria vendido além do disponível.

Alternativas consideradas:

- **Bloqueio otimista com novas tentativas:** funciona com pouca disputa, mas em SKUs muito disputados gera tempestades de retentativas e falsos fracassos quando o limite de tentativas se esgota.
- **`UPDATE` condicional por item** (`... where on_hand - reserved >= :q`): ótimo para um único item, mas um pedido com vários itens exige desfazer manualmente os itens já reservados quando um deles falha.
- **Bloqueio pessimista das linhas em ordem fixa:** serializa apenas pedidos que disputam os mesmos produtos e permite decidir o pedido inteiro antes de alterar qualquer linha.

## Decisão

- A reserva bloqueia as linhas de `inventory.product` com `SELECT ... FOR UPDATE`, sempre em ordem de SKU, verifica todos os itens e só então altera as quantidades. A ordem fixa evita deadlocks entre pedidos que contêm os mesmos produtos em ordens diferentes.
- O banco garante o invariante com `CHECK (reserved_quantity between 0 and on_hand_quantity)`, mesmo que algum caminho de código futuro ignore o bloqueio.
- A reserva é idempotente por pedido: a existência de reservas é verificada depois do bloqueio, e `unique (order_id, product_id)` impede duplicidade.
- A liberação bloqueia as reservas do pedido antes de devolver as quantidades, de modo que duas liberações concorrentes não devolvem o estoque duas vezes.
- `Product` mantém `@Version` para proteger outras alterações do cadastro contra atualizações perdidas.

## Consequências

O teste `StockReservationIT` comprova que 40 compradores concorrentes por 10 unidades resultam em exatamente 10 reservas e 30 rejeições, e que pedidos com os mesmos produtos em ordens opostas não entram em deadlock. Pedidos que disputam o mesmo SKU esperam uns pelos outros durante a transação, por isso as transações de reserva devem continuar curtas. Se um SKU se tornar gargalo, a alternativa será fragmentar o estoque desse produto, não remover o bloqueio.
