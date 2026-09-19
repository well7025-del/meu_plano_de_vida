# Vagas na Cloudflare

Mesma API de `services/api`, mesmas regras (`@vagas/core`), rodando na borda.

```bash
npx wrangler login     # uma vez, autoriza no navegador
npm run publicar       # da raiz do repositório
```

O que acontece: compila o motor, copia `apps/pwa` + `packages/core/dist` para
`services/worker/public`, e publica. O app sai direto da borda (sem cold
start); o Worker só é invocado em `/v1/*` e `/healthz`.

## O banco

D1 `vagas`, id `1263f2dd-ca41-48bc-810b-02f6d25f41d8`, já criado e com o
esquema aplicado. Para recriar do zero, ou subir um segundo ambiente:

```bash
npx wrangler d1 create vagas          # anote o id no wrangler.toml
npm run migrar -w @vagas/worker       # aplica schema.sql no banco remoto
```

O `database_id` versionado não é segredo — sem as credenciais da conta ele não
abre nada.

## Desenvolvendo

```bash
npm run dev -w @vagas/worker                          # http://localhost:8787
npx wrangler d1 execute vagas --local --file=schema.sql   # banco local, uma vez
```

## Custo

No plano gratuito: 100 mil requisições por dia no Worker e 5 milhões de linhas
lidas por dia no D1. Um piloto de bairro com algumas centenas de pessoas fica
com folga dentro disso — a conta de [DADOS.md](../../docs/DADOS.md) dá ~2
eventos por usuário por dia mais uma consulta a cada 25 s enquanto o app está
aberto.

## Limites conhecidos

O limite de taxa por origem vive na memória do isolate, então cada data center
conta separado e um atacante distribuído passa por cima. É proposital: ele
barra o caso ingênuo (cliente modificado em laço), e a defesa real contra
enxurrada é a própria Cloudflare, no nível da rede.
