# Arquitetura

## Princípio

**Tudo que pode ser decidido no celular é decidido no celular.** O servidor só
soma números e responde consultas por raio. Isso não é purismo: é o que torna o
sistema barato (o trabalho pesado roda em hardware alheio), privado (a
trajetória não trafega) e resistente a queda de rede (o app detecta offline e
envia depois).

## Componentes

```
apps/mobile ──────────────────────────────────────────────────────────┐
  expo-location (background)                                          │
    ↓ amostras                                                        │
  @vagas/core  MotionClassifier → ParkingDetector                     │
    ↓ ParkingEvent {lat, lon, t, confiança}                           │
  outbox SQLite (lote + atraso aleatório até 2 min)                   │
    ↓ HTTPS                                                           │
services/api ─────────────────────────────────────────────────────────┤
  POST /v1/events    valida, arredonda, grava em célula de 40 m       │
  GET  /v1/spots     agrega por célula, λ e P, devolve ≤300 pontos    │
  POST /v1/feedback  "achei" / "não tinha nada" → calibração          │
  GET  /v1/stats     volume e taxa de acerto                          │
    ↓                                                                 │
  SQLite (dev) / Postgres + PostGIS (produção)                        │
  retenção de 1 hora, poda a cada ingestão                            │
```

`@vagas/core` é compartilhado entre app e servidor de propósito: o mesmo código
que decide a probabilidade na tela decide no backend. Divergência entre os dois
seria um bug invisível e permanente.

## Escala

A carga é assimétrica e fácil: cada usuário gera **2 eventos por viagem** e
consulta o mapa a cada 20 s enquanto procura vaga.

Para uma cidade com 100 mil usuários ativos e 3 viagens/dia:

- **escrita**: 600 mil eventos/dia ≈ 7/s de pico — irrelevante para qualquer banco;
- **leitura**: o gargalo real. 10 mil pessoas procurando vaga ao mesmo tempo,
  3 consultas/min = 500 req/s. Resolvido com cache de 15 s por tile de mapa
  (Redis), porque duas pessoas na mesma quadra recebem exatamente a mesma
  resposta;
- **armazenamento**: com retenção de 1 h, a tabela nunca passa de alguns
  milhões de linhas. Esse é o tamanho inteiro do sistema.

O histórico (para previsão por horário) é uma tabela separada e agregada:
`(célula, dia_da_semana, hora) → λ médio`. Sem eventos individuais, sem
retenção longa.

## Em produção

| Camada | Escolha | Por quê |
|---|---|---|
| Banco | Postgres + PostGIS | consulta por raio com índice GIST; `earthdistance` já basta |
| Cache | Redis, TTL de 15 s por tile | 90% das leituras são repetidas |
| Ingestão | fila (SQS/NATS) na frente do banco | picos de reconexão em massa após queda de rede |
| Mapa offline | extrato OSM por cidade (~20 MB), embarcado | o `contextProvider` precisa ser síncrono e funcionar sem rede |
| Limite de taxa | por IP + prova de trabalho leve no cliente | não há conta para limitar por usuário |

## Partida a frio — o problema de verdade

Um app colaborativo sem usuários mostra um mapa vazio, e um mapa vazio não
atrai usuários. Esse, e não o algoritmo, é o risco que mata o projeto.

O tamanho do risco foi medido, não estimado: `npm run cobertura` roda o mesmo
bairro variando só a penetração. A utilidade do mapa salta de 41% para 73%
entre 6 e 18 motoristas com o app circulando em 0,5 km² — da ordem de algumas
centenas de instalações naquele bairro, não de milhões na cidade. A tabela
completa e as fontes de dados que encurtam esse caminho estão em
[DADOS.md](DADOS.md).

Três saídas, aplicadas em conjunto:

1. **Densidade antes de amplitude.** Lançar em *um* bairro, não em uma cidade.
   Em 4 km² com 2 mil usuários o mapa parece vivo; os mesmos 2 mil espalhados
   por São Paulo parecem um mapa quebrado.
2. **Não começar do zero.** Antes do primeiro usuário, a camada de oferta já
   existe: quantas vagas cabem em cada quarteirão sai do OSM + dados abertos de
   zona azul, e a demanda por horário sai dos dados de rotativo da prefeitura.
   Isso dá um mapa com *previsão* ("aqui às 19h costuma ter") no dia 1, que a
   detecção em tempo real só refina.
3. **Fonte que já se move.** Frotas (táxi, entrega, motorista de app) rodam o
   dia todo e passam por todas as ruas. Um SDK que só contribui detecções,
   negociado com uma empresa de frota, cobre um bairro inteiro sem depender de
   adoção orgânica.

## Bateria

O GPS contínuo em alta precisão consome de 5% a 8% de bateria por hora. Isso
desinstala o app na primeira semana. A estratégia tem três níveis:

1. **Parado ou a pé**: precisão balanceada, uma amostra a cada 20 s, atualizações
   adiadas em lote de 60 s. Custo próximo de zero.
2. **Dirigindo**: precisão alta, 3 s. É o único momento em que o dado importa,
   e é quando o celular costuma estar no carregador.
3. **Acordar por movimento**: o app é despertado pela API nativa de
   reconhecimento de atividade (`ActivityRecognition` no Android,
   `CMMotionActivity` no iOS) em vez de manter o GPS ligado esperando.

Meta: **menos de 1,5% de bateria por dia** para quem faz duas viagens. Se não
couber nisso, o produto não existe — e é por isso que o alvo está escrito aqui
e não numa planilha.

## Testes

```
packages/core/test   34 testes   motor, geometria, modelo de probabilidade
services/api/test    15 testes   validação, agregação, envelhecimento, abuso
packages/core/src/simulate.ts    60 motoristas sintéticos, mede acerto e erro em metros
```

O simulador é parte da suíte, não enfeite: qualquer mudança de limiar é medida
contra 120 eventos de verdade conhecida antes de entrar.
