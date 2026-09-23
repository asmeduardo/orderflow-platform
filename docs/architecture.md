# Arquitetura planejada

## Objetivos

O sistema deverá confirmar pedidos sem perder rastreabilidade quando um serviço ficar indisponível, uma mensagem for entregue mais de uma vez ou o processo for interrompido. A arquitetura priorizará consistência observável, recuperação e simplicidade operacional.

## Limites de domínio

- **Order:** recebe a intenção de compra, valida a requisição e controla o estado do pedido.
- **Inventory:** mantém a disponibilidade e registra reservas de estoque.
- **Notification:** reage a eventos confirmados e registra a entrega de notificações simuladas.

Cada contexto possuirá dados próprios. A comunicação assíncrona ocorrerá por eventos versionados no Kafka. Consultas síncronas serão usadas somente quando o resultado imediato fizer parte do contrato HTTP.

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
