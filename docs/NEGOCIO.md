# Monetização e primeiros passos

> Os números de custo são calculáveis e estão calculados. Os de receita
> dependem de contratos que ninguém assinou ainda: estão marcados como
> **premissa** e existem para serem derrubados por uma ligação, não para
> serem citados como fato.

## A regra que decide todas as outras

**A receita não pode vir da localização das pessoas.** Não por moralismo: por
sobrevivência. O produto pede a permissão mais invasiva que um celular tem, e
a única defesa é que o dado não vale nada para terceiros porque nunca sai
identificável. No dia em que a receita depender de vender rastro, todo o
desenho de [PRIVACIDADE.md](PRIVACIDADE.md) vira teatro, e a primeira
reportagem sobre isso encerra o assunto.

Então a monetização tem que sair de **transações que o usuário já faz** ou de
**tempo que ele economiza** — não da audiência dele.

## As cinco fontes, na ordem em que ligam

### 1. Rotativo / zona azul — a principal

O usuário do centro já paga estacionamento hoje, por outro aplicativo, e odeia
o processo. Levar esse pagamento para dentro do Vagas é receita transacional,
recorrente, e perfeitamente alinhada: quanto melhor o app acha vaga, mais
rotativo ele vende.

Dois caminhos, e o primeiro é muito mais rápido:

- **parceria com uma credenciada existente** — integração por API, divisão da
  comissão dela. Sem processo de credenciamento, sem caução, sem esperar edital.
- **credenciamento direto na prefeitura** — margem inteira, mas exige
  habilitação formal, prazos e garantias. É o passo 2, depois de o volume
  justificar.

**Premissa a validar na primeira semana:** qual a comissão que a credenciada
repassa por transação, e se ela aceita sub-distribuição. É a ligação mais
importante da lista inteira — a viabilidade financeira do produto depende
mais dessa resposta do que de qualquer decisão técnica.

### 2. Assinatura (Vagas Pro)

O que entra no plano pago é o que só serve a quem realmente sofre com vaga:

- navegação por probabilidade (a rota de busca, não o alfinete);
- aviso antecipado quando abre vaga perto do destino;
- previsão histórica por rua e horário;
- mais de um carro, lembretes ilimitados, histórico próprio.

O que **nunca** entra no plano pago: o mapa em si, as regras da via
("aqui é proibido"), e o alerta de irregularidade. Cobrar por não tomar multa
é vender seguro contra um problema que o próprio app poderia evitar.

Faixa de preço plausível no Brasil: **R$ 9,90 a R$ 14,90/mês**, com conversão
realista de **3% a 6%** dos ativos — que é a faixa usual de apps utilitários
com plano gratuito bom.

### 3. Comissão de estacionamento privado

Quando a probabilidade na região inteira está baixa, o app não tem resposta —
e é aí que ele oferece o estacionamento mais próximo, com preço. Comissão por
indicação convertida.

É a única linha que **melhora quando o produto falha**, o que é um risco de
incentivo: se mal calibrada, cria pressão para mostrar menos vagas de rua.
Mitigação: a oferta só aparece abaixo de um limiar fixo de probabilidade, o
limiar é público no app, e a métrica de tempo até estacionar é acompanhada
separadamente.

### 4. Dados agregados para o poder público (B2G)

Onde falta vaga, em que horário, quanto tempo as pessoas circulam procurando.
Nenhuma prefeitura tem isso em tempo real hoje. Contrato anual, ciclo de venda
longo (6 a 18 meses), ticket alto, e é a linha mais alinhada ao propósito.

Só faz sentido depois de 3 ou 4 bairros cobertos — antes disso não há amostra
que sustente a conversa.

### 5. Comércio local, com muito cuidado

Um restaurante ou clínica patrocinar "vagas perto daqui" é aceitável quando é
declarado, não altera a probabilidade exibida e não usa dado de quem viu. É a
última linha da lista de propósito: é a que mais facilmente corrói a confiança
que as quatro anteriores dependem.

## Quanto custa servir um usuário

Aqui os números são apuráveis, e um deles costuma surpreender:

| Item | Custo por usuário/mês | Observação |
|---|---:|---|
| **Mapa (Google Maps SDK)** | **~R$ 1,00–1,50** | cobrado por carregamento de mapa; ~30 aberturas/mês por usuário ativo |
| **Mapa (MapLibre + tiles próprios)** | **~R$ 0,03** | PMTiles de extrato do OSM em CDN; custo é banda |
| API + banco + cache | ~R$ 0,05 | retenção de 1 h mantém a base minúscula |
| Notificações push | ~R$ 0,00 | FCM/APNs são gratuitos |
| **Total com mapa próprio** | **~R$ 0,10** | |

**O maior custo variável de um app de mapa é o mapa.** Com o SDK do Google,
servir 10 mil usuários custa da ordem de R$ 10–15 mil por mês e cresce linear;
com MapLibre e tiles próprios, custa algumas centenas. O `apps/mobile` deste
repositório usa `react-native-maps` (Google no Android) porque é o caminho mais
curto para um piloto — **trocar por MapLibre antes de passar de mil usuários
é o item de engenharia com maior retorno financeiro do projeto inteiro.**

(Preços de SDK de mapa mudam; confirme a tabela vigente antes de orçar.)

Custos fixos do início: conta de desenvolvedor Apple (US$ 99/ano) e Google
(US$ 25 uma vez), servidor (~R$ 150/mês), domínio, e contador/PJ. A App Store
e o Google Play ficam com 15% das assinaturas no programa de pequenos
desenvolvedores (até US$ 1 mi/ano) — 30% acima disso.

## Quanto entra por usuário

Com as premissas marcadas, por usuário **ativo** por mês:

| Fonte | Premissa | Receita/usuário/mês |
|---|---|---:|
| Rotativo | 40% usam rotativo, R$ 80/mês cada, comissão de 5% | R$ 1,60 |
| Assinatura | 4% convertem a R$ 11,90, menos 15% de loja | R$ 0,40 |
| Estacionamento | 0,2 indicação/mês, R$ 2,00 por conversão | R$ 0,40 |
| **Total** | | **~R$ 2,40** |

Contra R$ 0,10 de custo variável, a margem por usuário é confortável. **O
negócio não morre de economia unitária — ele morre de densidade.** Os custos
fixos (servidor, lojas, contabilidade: ~R$ 500/mês) se pagam com cerca de
**250 usuários ativos**, que é exatamente a ordem de grandeza que
[DADOS.md](DADOS.md) mostrou ser necessária para o mapa funcionar em um bairro.

Essa coincidência é a tese inteira do projeto: **o ponto em que o produto
começa a funcionar é quase o mesmo em que ele começa a se pagar.** Não é um
negócio que precisa de escala nacional para fechar a conta — precisa de um
bairro que funcione, e depois repetir.

## Os primeiros 90 dias

### Dias 1–15 — validar sem escrever código

1. **Escolher o bairro.** Critérios, nesta ordem: escassez real de vaga,
   rotativo ativo (é a receita), densidade de destinos (comércio, clínicas,
   escritórios) e comunidade organizada (associação, grupo de prédio).
   Um bairro. Não dois.
2. **Medir a linha de base.** Cronometrar, por uma semana, quanto tempo leva
   entre chegar no bairro e desligar o carro estacionado — com você, com 5
   conhecidos, em horários diferentes. **Sem esse número não existe prova de
   valor depois**, e é o número que abre todas as portas da lista abaixo.
3. **Fazer as três ligações:** uma credenciada de zona azul (comissão e
   sub-distribuição), a associação comercial do bairro (acesso aos primeiros
   usuários), e a secretaria de mobilidade (dados abertos de rotativo).
4. **Resolver a parte jurídica:** PJ, política de privacidade, base legal e
   relatório de impacto (RIPD). Monitoramento contínuo de localização é
   tratamento de alto risco; começar sem isso é construir sobre dívida.

### Dias 16–45 — piloto fechado, 100 pessoas

5. Trocar o mapa por **MapLibre + PMTiles** antes de qualquer distribuição.
6. Carregar a camada de oferta e a previsão histórica a partir do dado aberto
   de rotativo, para que o app **já seja útil no dia 1, com zero usuários**.
7. Publicar em TestFlight e teste fechado do Google Play.
8. Recrutar 100 pessoas **do mesmo bairro** — prédios, comércio da rua, grupo
   de WhatsApp. Cem no bairro valem mais que dez mil espalhadas.
9. Instrumentar a métrica norte (tempo até estacionar) e a taxa de confirmação
   desde o primeiro dia.

### Dias 46–90 — provar e ligar a receita

10. Abrir o bairro ao público e perseguir os **500 usuários ativos**.
11. Ligar o pagamento de rotativo assim que houver contrato.
12. Publicar o resultado do piloto — "tempo médio de procura caiu de X para Y"
    — que é o material que vende o segundo bairro, a conversa com a prefeitura
    e qualquer rodada de investimento.
13. Só então: segundo bairro, assinatura, e a conversa B2G.

## O que fazer nesta semana

1. Escolher o bairro e escrever em uma frase por que é ele.
2. Cronometrar as cinco primeiras buscas por vaga, no papel. Começa hoje.
3. Ligar para uma credenciada de zona azul e perguntar a comissão.
4. Verificar se a sua cidade publica dado de rotativo em formato aberto.
5. Trocar `react-native-maps` por MapLibre no `apps/mobile`.

## O critério de parada

Definido **antes** de começar, porque depois ninguém consegue:

> Se, ao fim do piloto com 500 usuários no bairro, o tempo médio entre chegar
> na região e estacionar **não cair pelo menos 20%** em relação à linha de
> base medida nos dias 1–15, o produto não funciona e não adianta insistir em
> mais bairros.

Nesse cenário ainda sobra algo aproveitável: a camada de rotativo, regras da
via e "onde deixei o carro" é um produto útil sozinho, sem rede nenhuma — e é
para onde o projeto deve pivotar em vez de morrer.
