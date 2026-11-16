# ADR 0006 — Proteger a API como resource server OAuth 2.0 com escopos

## Estado

Aceita.

## Contexto

A API recebe pedidos de canais de venda (integrações máquina a máquina) e consultas da operação. É preciso separar quem apenas consulta de quem altera pedidos e estoque, sem que a API guarde senhas ou sessões. O ambiente local precisa emitir tokens reais para experimentar a API pelo Swagger UI e pela coleção HTTP, e os testes não podem depender de um provedor de identidade em execução.

## Decisão

- A aplicação é um resource server OAuth 2.0 sem estado: toda chamada a `/api/**` exige um access token JWT no cabeçalho `Authorization: Bearer`.
- O token é validado por assinatura, emissor (`iss`), audiência (`aud = orderflow-api`) e validade (`exp`/`nbf`). As chaves públicas vêm do endpoint JWKS do emissor e são buscadas sob demanda, de modo que a API inicia mesmo com o emissor fora do ar.
- Dois escopos independentes: `orderflow.read` para consultas (`GET`) e `orderflow.operate` para alterações (criar produto, criar e cancelar pedido). Negar é o padrão para qualquer outra rota; apenas a documentação OpenAPI é pública.
- Respostas `401` e `403` usam `ProblemDetail`, como o restante da API, e mantêm o desafio `WWW-Authenticate: Bearer` da RFC 6750 (o Spring Security 7 também anuncia ali os metadados de recurso protegido da RFC 9728).
- CSRF fica desabilitado porque a API não usa cookies; tokens bearer não são enviados automaticamente pelo navegador.
- Para desenvolvimento local, o módulo `dev-auth-server` usa o Spring Authorization Server com dois clientes `client_credentials` (`orderflow-reader` e `orderflow-operator`), cujos segredos vêm do `.env`. Ele acrescenta a audiência `orderflow-api` aos tokens. Em produção, basta apontar `ORDERFLOW_ISSUER_URI` (e, se o caminho for diferente de `/oauth2/jwks`, `ORDERFLOW_JWK_SET_URI`) para o provedor gerenciado escolhido.
- Os testes usam o suporte a JWT do `spring-security-test`. A validação completa (assinatura, emissor, audiência e expiração) é testada com tokens assinados por uma chave RSA gerada durante a execução, nunca versionada, cuja parte pública é servida como JWKS por um servidor HTTP local do próprio teste.
- A chave de idempotência passa a ser isolada pelo cliente (`sub` do token), como previsto na ADR 0005. O teste que motivou a mudança mostrou que, com chaves globais, um cliente recebia o pedido de outro ao repetir a mesma chave.

## Consequências

A API não armazena credenciais e pode trocar de provedor de identidade sem mudança de código. O servidor de autorização local é uma ferramenta de desenvolvimento: guarda clientes em memória, gera uma nova chave de assinatura a cada inicialização e não deve ser exposto em produção. O painel React da fase 5 usará o fluxo authorization code com PKCE, que exigirá cadastrar usuários no provedor escolhido.
