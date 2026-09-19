# De onde vêm os dados antes de existirem usuários

## A resposta curta: não dá para ver celular alheio

Um app não consegue ler a localização de um aparelho que não está rodando o
código dele. Isso não é uma limitação que se contorna com esforço — é o
desenho do Android e do iOS:

- cada app roda em um *sandbox* e só enxerga os sensores que **o próprio
  usuário concedeu àquele app**;
- não existe API, em nenhum dos dois sistemas, que devolva a posição de outro
  aplicativo ou de outro aparelho;
- varrer o ar em busca de celulares (Wi-Fi, Bluetooth, sinal de celular) deixou
  de funcionar na prática — iOS e Android embaralham o endereço de rede a cada
  sondagem desde 2014/2019 — e, além de exigir equipamento na rua, é
  interceptação de comunicação: crime, não atalho.

Então sim: **o dado de movimento só existe onde o seu código está rodando com
permissão.** Mas o seu código não precisa estar dentro do *seu* app.

## Os caminhos que existem de verdade

Em ordem do que eu faria primeiro:

### 1. Dados de rotativo e zona azul (o melhor atalho no Brasil)

Cada pagamento de zona azul é, literalmente, um evento de estacionamento com
lugar, horário e duração. Não cobre rua livre, mas cobre exatamente as vagas
mais disputadas do centro — e é dado que a prefeitura e as operadoras já têm.
Em muitas cidades é aberto ou obtível por convênio.

Isso dá, no dia 1 e sem nenhum usuário: mapa de oferta (quantas vagas há em
cada quarteirão), curva de demanda por hora e dia, e a hora em que cada vaga
paga vence — que é o instante em que ela provavelmente vai abrir.

### 2. Frotas

Táxi, entrega, logística, locadora, telemetria de seguradora. Quinhentos
veículos cobrem uma cidade inteira melhor que cinquenta mil usuários casuais,
porque rodam o dia todo e passam por todas as ruas. É relação contratual, com
consentimento claro de quem dirige, e resolve cobertura sem depender de adoção
orgânica.

Limitação honesta: motorista de aplicativo e entregador param em fila dupla o
tempo todo. Esses trajetos ensinam muito sobre **oferta** e **fluxo**, e pouco
sobre vaga legítima — precisam da camada de regras da via para não virar ruído.

### 3. Carro conectado

Smartcar, Otonomo e as APIs das próprias montadoras entregam ignição, câmbio em
P e porta. Isso substitui a inferência por um fato. É o dado mais limpo que
existe para este problema, e o mais caro.

### 4. SDK em apps de terceiros

É assim que a indústria de dados de trânsito se formou: um SDK leve embarcado
em apps que já pedem localização (rádio, tempo, combustível, cupons), que
contribui detecções para a sua rede. Funciona e escala rápido.

**Mas é o caminho com o maior risco reputacional e regulatório do conjunto.**
Foi exatamente esse modelo que levou empresas de dados de localização a acordos
e proibições com o FTC nos Estados Unidos, por consentimento obtido de forma
opaca. Se for usado aqui, tem que ser com consentimento específico, legível e
revogável na tela do app hospedeiro — não enterrado num termo de uso. Feito
mal, é a maneira mais rápida de matar a confiança no produto inteiro.

### 5. Comprar dado de sonda pronto

INRIX, TomTom, HERE vendem *probe data* agregado. Caro, e desenhado para fluxo
de via, não para o meio-fio — ajuda pouco no problema específico de vaga.

### 6. Visão computacional em frota

Câmera de painel contando espaços vazios no meio-fio. Muda a natureza do dado
("há 3 vagas neste quarteirão") em vez de inferir de movimento. Caro,
processamento na borda, consentimento reforçado. Só se paga em alta densidade.

## Quantos usuários são necessários, de fato

A intuição de que "só funciona se muita gente instalar ao mesmo tempo" está
certa na direção e errada na escala. O que importa não é o total de instalações
na cidade — é a **densidade de motoristas ativos num bairro**.

Medido com `npm run cobertura` (bairro de 0,52 km², 1176 vagas de meio-fio,
60 carros circulando, 4 h simuladas, média de 3 rodadas):

| Motoristas com o app circulando | Pontos no mapa | Precisão | Utilidade |
|---:|---:|---:|---:|
| 1 | 1,1 | 90% | 0% |
| 3 | 3,2 | 95% | 18% |
| 6 | 6,4 | 93% | **41%** |
| 12 | 12,0 | 91% | 49% |
| 18 | 17,1 | 91% | **73%** |
| 30 | 26,2 | 93% | 84% |
| 48 | 36,1 | 92% | 88% |

*Utilidade* = das vezes em que alguém foi procurar vaga, em quantas o app tinha
um ponto **verdadeiro** a menos de 150 m do destino.

Duas leituras importam:

**A precisão quase não muda.** Fica em 90–95% desde o primeiro usuário. O mapa
nunca mente muito — ele só fica vazio. Isso é bom: o dano de entrar cedo é
"não achei nada", não "o app me enganou".

**A utilidade tem um joelho.** Entre 6 e 18 motoristas ativos ela salta de 41%
para 73%. Abaixo disso o app é um enfeite; acima, é ferramenta.

Convertendo para instalações, com a hipótese de que cada usuário gera 2 eventos
por dia distribuídos em 12 horas úteis (a tabela mostra 19 eventos/h com 6
motoristas ativos e 85 eventos/h com 18):

- **~100 usuários** que estacionam regularmente naquele 0,5 km² → utilidade 41%
- **~500 usuários** no mesmo 0,5 km² → utilidade 73%

Ou seja: **da ordem de algumas centenas de pessoas em um bairro**, não milhões
numa cidade. Isso é uma campanha de prédio, uma parceria com o comércio de uma
rua, um grupo de bairro. É atingível — desde que o lançamento seja em um
bairro, e não em uma capital.

## O que faz alguém instalar antes de a rede existir

Este é o outro lado da mesma moeda, e é onde o produto se salva: as funções que
**não dependem de nenhum outro usuário** precisam ser boas o bastante sozinhas.

| Função | Precisa de rede? |
|---|---|
| Onde eu deixei o carro (automático) | não |
| Aviso de vencimento do rotativo | não |
| Pagamento da zona azul | não |
| "Aqui é proibido estacionar das 7h às 9h" | não |
| Previsão histórica ("nesta rua, terça às 19h, 65%") | não — vem de dado aberto |
| Pontos verdes em tempo real | **sim** |

Um app que só entrega a última linha morre esperando massa crítica. Um app que
entrega as cinco primeiras no dia 1 tem motivo de instalação independente — e
cada instalação dessas vira, de graça, um sensor que constrói a sexta.
