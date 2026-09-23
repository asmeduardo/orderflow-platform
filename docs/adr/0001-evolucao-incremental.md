# ADR 0001 — Evoluir do monólito modular para serviços orientados a eventos

## Estado

Aceita para a implementação inicial.

## Contexto

O projeto precisa demonstrar mensageria e operação distribuída, mas começar diretamente com vários serviços acrescentaria infraestrutura antes que os limites do domínio estivessem comprovados.

## Decisão

A primeira versão será um monólito modular com limites explícitos entre pedidos e estoque. Depois que os fluxos, invariantes e testes estiverem estáveis, o contexto de estoque será extraído e passará a consumir eventos do Kafka. O histórico de commits deverá preservar essa evolução.

## Consequências

O projeto terá uma primeira entrega executável mais cedo e permitirá comparar os custos do acoplamento local e da comunicação distribuída. A extração exigirá contratos de evento, idempotência, observabilidade e tratamento de falhas que não seriam necessários no fluxo local.
