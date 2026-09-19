# Vagas

Sinalizador colaborativo de vagas de estacionamento na rua.

O app percebe sozinho, pelo perfil de movimento do celular, quando um carro
**sai** de uma vaga e quando um carro **ocupa** uma vaga — e transforma isso em
pontos verdes no mapa de quem está procurando lugar para estacionar.

```
  caminhada  →  carro em movimento         =  vaga LIBERADA  (ponto verde)
  carro      →  parado  →  caminhada       =  vaga OCUPADA   (apaga o ponto)
```

Ninguém precisa apertar nada. É esse o ponto: todo app colaborativo de vaga que
depende do usuário avisar morre por falta de aviso.

## Estado deste repositório

| Parte | O que é | Situação |
|---|---|---|
| `packages/core` | Motor de detecção + modelo de probabilidade, TypeScript puro | **Funciona, 34 testes** |
| `services/api` | Ingestão de eventos e consulta de pontos | **Funciona, 15 testes** |
| `apps/pwa` | **App web instalável, para testar na rua hoje** | **Funciona: `npm run testar`** |
| `services/worker` | Mesma API em Cloudflare Workers + D1, para o endereço ficar fixo | **Funciona: `npm run publicar`** |
| `apps/mobile` | App Expo / React Native (mapa, detecção em background) | Código completo, precisa de build nativo |
| `apps/web-demo` | Simulador visual do sistema inteiro rodando | [Abrir](https://claude.ai/artifact/S75y2k8ED2SioRFks24Pxe) |
| `docs/` | Algoritmo, arquitetura, privacidade e roadmap de produto | — |

## Testar na rua, hoje

**Pelo celular, sem terminal:** criar um token na Cloudflare e colar num campo
do GitHub. O passo a passo, tela por tela, está em
[`docs/PUBLICAR.md`](docs/PUBLICAR.md).

**Com terminal:**

```bash
npx wrangler login    # uma vez
npm run publicar      # publica na Cloudflare: endereço fixo, sempre no ar
```

Quando a publicação falha, o `publicar` traduz o erro da Cloudflare em uma
instrução do que fazer, em vez de despejar o log.

ou, para rodar da sua própria máquina enquanto desenvolve:

```bash
npm run testar
```

O `publicar` sobe app e API na borda da Cloudflare (banco D1 e conta já
configurados) — o endereço do teste é `https://vagas.well7025.workers.dev`. O `testar` sobe
tudo localmente e, se você tiver `cloudflared`, imprime um endereço HTTPS
temporário. Cada pessoa abre no celular e
adiciona à tela inicial — é um app instalável, com radar das vagas próximas,
aviso por voz e registro de diagnóstico.

O passo a passo do piloto, a mensagem pronta para o grupo e o que anotar estão
em [`docs/TESTE-DE-CAMPO.md`](docs/TESTE-DE-CAMPO.md).

## Desenvolvendo

```bash
npm install
npm test          # 62 testes: motor + API
npm run simulate  # simulação de campo com 60 motoristas e medição de acerto
npm run cobertura # quantos usuários o bairro precisa para o mapa ser útil
npm run api       # sobe a API + o app em http://localhost:8787
```

O simulador gera trajetos sintéticos de GPS com ruído, sabe onde cada carro
realmente estacionou e compara com o que o motor detectou:

```
=== Vagas — simulacao de campo ===
motoristas simulados......... 60
eventos reais (verdade)...... 120
eventos detectados........... 120
cobertura (recall)........... 100.0%
precisao..................... 100.0%
erro do ponto: mediana....... 20.4 m
trajetos so de passagem...... 20 percursos, 0 eventos gerados
```

Esses números são de trajetos **sintéticos**: eles provam que a lógica está
correta e que semáforo/trânsito não viram falso positivo, não que o app acerta
isso na rua. O que a rua vai quebrar está listado em
[`docs/ALGORITMO.md`](docs/ALGORITMO.md#o-que-a-rua-quebra).

O app mobile mora fora do workspace (depende do toolchain nativo do Expo):

```bash
cd apps/mobile && npm install && npx expo run:android
```

## Como funciona, em uma tela

```
  ┌─ celular ────────────────────────────────┐      ┌─ servidor ──────────┐
  │  GPS + sensor de movimento               │      │                     │
  │        ↓                                 │      │  eventos anônimos   │
  │  classificador de modo (mediana +        │      │  agrupados em       │
  │  histerese: parado / a pé / veículo)     │      │  células de 40 m    │
  │        ↓                                 │      │        ↓            │
  │  máquina de estados → evento             │─────▶│  λ = Σ saídas −     │
  │  (ponto, horário, confiança)             │ só o │      Σ chegadas,    │
  │        ↓                                 │ evento  com decaimento    │
  │  fila local, envio em lote com atraso    │      │        ↓            │
  └──────────────────────────────────────────┘      │  P = 1 − e^−λ       │
                                                    └─────────────────────┘
```

A trajetória nunca sai do aparelho. O servidor recebe pontos soltos, sem conta,
sem identificador, com retenção de 1 hora — ele é um agregador burro de
propósito. Detalhes em [`docs/PRIVACIDADE.md`](docs/PRIVACIDADE.md).

## Simulador visual

`apps/web-demo/index.html` abre direto no navegador (ou
[aqui](https://claude.ai/artifact/S75y2k8ED2SioRFks24Pxe)): um bairro de 720 m
com 1176 vagas de meio-fio, 60 motoristas e os mesmos limiares do motor real.
Dá para baixar a cobertura do app para 10% e ver o mapa esvaziar — que é o
risco de partida a frio descrito em [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md).

## Documentação

- [`docs/ALGORITMO.md`](docs/ALGORITMO.md) — como a detecção funciona, os
  limiares, e cada falso positivo que ela precisa derrubar
- [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md) — componentes, escala, custo,
  e o problema de partida a frio
- [`docs/PRIVACIDADE.md`](docs/PRIVACIDADE.md) — o que sai do celular e por quê
- [`docs/DADOS.md`](docs/DADOS.md) — de onde vêm os dados antes de existirem
  usuários, e quantos usuários o bairro precisa de fato
- [`docs/PRODUTO.md`](docs/PRODUTO.md) — o roadmap: o que eu acrescentaria
  além do mapa de pontinhos verdes, e em que ordem
- [`docs/NEGOCIO.md`](docs/NEGOCIO.md) — como isso vira receita, quanto custa
  servir um usuário, e o plano dos primeiros 90 dias
- [`docs/REVISAO-IDEIAS.md`](docs/REVISAO-IDEIAS.md) — avaliação de nove ideias
  de funcionalidade, com o que foi medido no simulador
- [`docs/PUBLICAR.md`](docs/PUBLICAR.md) — como colocar o app no ar, com e sem
  terminal
- [`docs/TESTE-DE-CAMPO.md`](docs/TESTE-DE-CAMPO.md) — como rodar o piloto com
  amigos e o que anotar
