# Vender para prefeituras: o que muda

> Aviso de honestidade: isto é um negócio **diferente** do app de motorista.
> Outro comprador, outro ciclo de venda, outro produto, outra empresa. O motor
> de detecção continua sendo uma vantagem real — mas não é o que a prefeitura
> compra.

## A verdade desconfortável

Uma prefeitura não compra "um app de estacionamento". Ela compra um **sistema
de arrecadação com prestação de contas**. O app do motorista é a menor peça
disso, e frequentemente nem é a peça que decide a licitação.

O que está de fato no edital:

| Módulo | O que é | Peso na decisão |
|---|---|---|
| **Venda do crédito** | app, web, pontos credenciados, PIX, cartão, SMS | médio |
| **Fiscalização** | app do agente: placa, foto, GPS, notificação | **alto** |
| **Gestão** | setores, tarifas, horários, isenções (PCD, idoso, morador) | alto |
| **Financeiro** | arrecadação, repasse, conciliação, split | **alto** |
| **Prestação de contas** | relatórios para o TCE, transparência, auditoria | **alto** |
| **Integrações** | sistema de multas, DETRAN, contabilidade municipal | alto |

Repare: **três dos cinco itens de maior peso não têm nada a ver com achar
vaga.** São dinheiro público, fiscal e auditoria. É aí que as concorrentes
ganham, e é aí que um projeto novo perde por falta de histórico.

## Onde o nosso motor é único

Dito isso, existe uma coisa que nenhuma das incumbentes entrega, porque
nenhuma delas tem sensor: **a prefeitura não sabe qual é a ocupação real das
suas vagas.** Ela sabe o que foi *pago*. A diferença entre os dois é exatamente
o buraco onde mora o dinheiro perdido.

Quatro produtos saem daí, todos apoiados no que já existe em `packages/core`:

### 1. Ocupação real por trecho e horário

O que hoje é estimado por amostragem manual (um estagiário andando com prancheta
uma vez por ano) passa a ser medido continuamente. É o insumo de tudo o que vem
abaixo.

### 2. Mapa de evasão

```
  eventos de estacionamento detectados  −  tíquetes pagos  =  evasão estimada
```

Por quarteirão, por hora, por dia da semana. Nenhuma prefeitura brasileira tem
esse número hoje com granularidade de rua. E é um número que se converte
direto em conversa de gabinete: *"o senhor perde R$ X por mês na Rua Y, entre
14h e 18h"*.

Esse é o argumento de venda. Não é "nosso app é bonito" — é "você está perdendo
dinheiro em lugares que você não sabe quais são".

### 3. Fiscalização dirigida

Agente de trânsito é recurso escasso e caro. Hoje ele caminha por rota fixa ou
por intuição. Com o mapa de evasão, ele recebe a rota de **maior probabilidade
de irregularidade** naquele momento. A mesma equipe cobre mais e autua mais
onde importa.

É a funcionalidade com o retorno mais fácil de demonstrar num piloto de 30 dias.

### 4. Preço por demanda

O modelo de Donald Shoup, aplicado em São Francisco no SFpark: ajustar a tarifa
por trecho até que a ocupação fique em torno de 85% — nível em que sempre há
uma vaga livre por quarteirão e a procura por vaga desaparece. Reduzir o
tráfego de procura é meta de mobilidade, não de arrecadação, e isso muda o tom
da conversa com o poder público.

Só é possível com medição contínua de ocupação. Sem sensor, sem preço dinâmico.

## Os três caminhos de entrada

Uma empresa nova não vence uma licitação grande de rotativo. Quem acha que vai,
perde 18 meses descobrindo. Os caminhos reais, do mais rápido ao mais lento:

### A. Ser fornecedor da operadora (mais rápido)

Quem já opera o rotativo da cidade tem o contrato, a marca e o risco — e **não
tem o dado de ocupação**. Vender a camada de inteligência para ela é uma venda
B2B comum: sem licitação, sem certidão, sem atestado de capacidade técnica.

É o caminho que eu tentaria primeiro. O ticket é menor, o ciclo é de meses em
vez de anos, e cada contrato vira atestado para o caminho C.

### B. Piloto por dispensa de licitação

Contratações de pequeno valor dispensam licitação (Lei 14.133/2021; o teto é
corrigido todo ano — confirme o valor vigente). Dá para um piloto de 60 a 90
dias em alguns setores, com produto restrito: **medir ocupação e evasão**, sem
tocar em arrecadação.

Vantagem: entrega um relatório que a prefeitura nunca teve, gera atestado de
capacidade técnica, e não disputa com a incumbente — complementa.

### C. Licitação cheia (último)

Pregão eletrônico ou concorrência para o sistema inteiro. Exige CNPJ maduro,
certidões, garantia, atestados de serviço semelhante, e capacidade de operar
com pagamento em 30–60 dias depois do empenho. Só faz sentido depois de A e B
terem gerado histórico.

## O que falta construir

Mapeado contra o que já existe no repositório:

| Componente | Situação |
|---|---|
| Detecção de chegada e saída | **pronto** (`packages/core`, 62 testes) |
| Agregação por célula e probabilidade | **pronto** |
| Cadastro de setores, tarifas e horários | a fazer |
| Motor de tarifação com isenções | a fazer |
| Emissão e validação do crédito | a fazer |
| App do agente (offline, OCR de placa, foto, GPS) | a fazer |
| Notificação de irregularidade e integração com multas | a fazer |
| Painel de gestão e relatórios TCE | a fazer |
| Trilha de auditoria assinada, LGPD, e-MAG | a fazer |
| Multi-tenant por município | a fazer |

Estimativa honesta para o sistema completo: **12 a 18 meses com equipe
dedicada**. Para o produto restrito do caminho B (ocupação + evasão + rota de
fiscalização): **2 a 3 meses**, porque o motor já existe.

Essa diferença — 3 meses contra 18 — é toda a razão para começar pelo caminho B.

## O conflito de incentivo que eu recusaria

Há duas formas de ser remunerado por um sistema de rotativo:

- **percentual da arrecadação** (crédito vendido);
- **percentual das multas**.

A segunda é comum e é uma armadilha. Ela alinha o fornecedor com *autuar mais*,
não com *a rua funcionar melhor* — e o dia em que isso vira notícia, o contrato
e a reputação vão juntos. Eu assinaria só a primeira, e colocaria no contrato
a meta de **redução do tempo de procura por vaga**, que é o que a população
sente.

Isso não é só ética: é a diferença entre um fornecedor que a próxima gestão
renova e um que ela cancela.

## A sinergia que justifica os dois negócios

O app de motorista tem um problema conhecido, medido em [DADOS.md](DADOS.md):
partida a frio. Ele só fica útil com algumas centenas de usuários no mesmo
bairro.

Vender para a prefeitura resolve isso de lado: **o contrato dá acesso aos dados
de rotativo**, que são exatamente a melhor fonte para popular o mapa antes de
existir massa crítica de usuários. E o app de motorista, por sua vez, vira o
canal de venda do crédito — com o qual se ganha no caminho A.

Os dois negócios se alimentam. Mas são dois, com times e tempos diferentes, e
tentar os dois ao mesmo tempo com uma pessoa só é o jeito mais seguro de não
entregar nenhum.

## O que eu faria, em ordem

1. **Terminar o piloto de bairro** ([TESTE-DE-CAMPO.md](TESTE-DE-CAMPO.md)).
   Sem detecção provada na rua, nada abaixo existe.
2. **Cruzar com dados abertos de rotativo** da sua cidade e produzir *um*
   relatório de evasão de um bairro. Esse PDF é o produto de venda inteiro.
3. **Levar o relatório para a operadora** que já atende a cidade (caminho A).
   A conversa começa com um número que ela não tem.
4. **Em paralelo**, procurar o secretário de mobilidade de uma cidade média
   (100 a 400 mil habitantes) — grande o bastante para ter rotativo, pequena o
   bastante para você falar com quem decide. Propor o piloto do caminho B.
5. Só depois: o sistema completo e a licitação.

## O risco que eu vigiaria

Governo paga bem e atrasa muito. Um contrato público de R$ 200 mil que demora
9 meses para ser pago quebra uma empresa de duas pessoas, mesmo com o contrato
assinado na gaveta. Antes de perseguir esse mercado, tenha caixa para 6 meses
de operação **sem** a receita que está contratada — ou um sócio que já conheça
o ciclo de empenho, liquidação e pagamento.
