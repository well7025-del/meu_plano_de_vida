# Revisão das ideias 6 a 14

Avaliação honesta, com medição onde foi possível medir. Três delas mudaram o
código; duas eu mudaria antes de construir; uma eu construiria diferente do
proposto.

## Placar

| # | Ideia | Veredito |
|---|---|---|
| 8 | "Estou procurando vaga" | **A melhor da lista.** Destrava outras três |
| 11 + 12 | Histórico e previsão | **Forte, e subestimada** — é a resposta à partida a frio |
| 9 | Navegação para o trecho, não para o ponto | **Certa**, com um ajuste no fim da rota |
| 6 | Confirmação colaborativa | **Já construída** — mas o papel dela é menor do que parece |
| 13 | "Vou sair daqui" | **Maior potencial e maior risco.** Precisa de outro desenho |
| 10 | Detectar o lado da rua | **Viável**, e o sinal principal não está na sua lista |
| 7 | Botão manual | **Metade sim, metade não** — depende de qual botão |
| 14 | Reputação | **Problema certo, mecanismo errado** |

---

## 6. Confirmação colaborativa — já existe, e faz menos do que parece

O mecanismo João→Maria já está implementado: a chegada entra no modelo com
peso negativo e apaga o ponto (`availability.ts`, teste *"chegada cancela
saida: alguem ja ocupou a vaga"*). Sua descrição do ciclo está exata.

**Mas a intuição de que é isso que mantém o mapa honesto está errada.** Rodei
o bairro com e sem o cancelamento por chegada:

| Cobertura | Precisão com chegadas | Sem chegadas | Diferença |
|---:|---:|---:|---:|
| 10% | 93% | 92% | 0,1 pp |
| 30% | 91% | 90% | 0,5 pp |
| 50% | 93% | 92% | 1,0 pp |
| 80% | 92% | 90% | 1,3 pp |

Mesmo com ocupação de 99% (só 12 vagas livres no bairro inteiro), a diferença
ficou em 1 ponto percentual.

A razão é aritmética: com 30% de cobertura, **70% das ocupações são invisíveis**
— quem tomou a vaga não tinha o app. O cancelamento só conserta a fração
detectável, enquanto o decaimento temporal conserta 100% dos casos, com atraso.
**É o decaimento que faz o trabalho pesado.** A confirmação colaborativa é um
refinamento bom, não o alicerce.

E ela vem com um efeito colateral que sua descrição não menciona: no instante
em que Maria vai até a vaga *porque o app mostrou*, ela deixou de ser uma
observadora e passou a ser uma concorrente. Ver o item 13.

## 7. Botão manual — o vermelho sim, o verde com muito cuidado

São dois botões com naturezas opostas, e tratá-los igual é o erro.

**🔴 "não tinha nada" — essencial, e já está construído.** É corretivo: alguém
que está fisicamente no local desmentindo o app. Pesa 0,85 no modelo, o que
derruba o ponto na hora (`app.ts`, rota `/v1/feedback`).

**🟢 "liberei uma vaga" — é o vetor de abuso mais óbvio do sistema.** Três
problemas concretos:

1. A detecção automática já pega essa saída em ~2 min com confiança acima de
   0,8. O botão compra dois minutos ao custo de abrir a porta para dado falso.
2. Um cliente modificado poderia despejar saídas inventadas — para esvaziar
   uma rua, para inflar reputação, ou por diversão.
3. A pessoa aperta, e então decide ficar mais 20 minutos. Ninguém está
   mentindo, e o dado está errado do mesmo jeito.

**O desenho que eu faria:** o botão não *cria* o evento — ele **eleva a
confiança de um evento que os sensores já suspeitam.** Se o celular está vendo
o padrão de movimento compatível, o toque leva a confiança de 0,7 para 0,95 e
antecipa a publicação. Se não há nenhum sinal de movimento, o toque vale muito
pouco. Assim o botão ajuda quando a detecção automática falha (garagem coberta,
sem sinal) e não serve como porta de entrada para invenção.

## 8. "Estou procurando vaga" — a melhor ideia da lista

Não é uma tela; é um **modo de operação**, e ele destrava quatro coisas de uma
vez:

1. **Atribuição.** Só quem sabe *quem está procurando* pode distribuir vagas
   diferentes para motoristas diferentes. É a única solução para a corrida.
2. **Bateria.** GPS de alta precisão só nesse modo. Fora dele, o app quase não
   custa nada.
3. **A métrica norte.** O modo marca o início da busca; o evento de chegada
   marca o fim. O tempo entre os dois é a única prova de que o produto funciona.
4. **A linha do plano pago.** É a função que quem sofre com vaga usa todo dia.

**Uma correção na sua maquete.** Ordenar por distância em linha reta
(`150 m → 🟢`) é a métrica errada dirigindo: uma vaga a 150 m numa rua de mão
contrária são 800 m de volta ao quarteirão. Tem que ser **tempo de rota**,
respeitando sentido de circulação e conversões proibidas. O número que o
motorista vê deveria ser "2 min", não "150 m".

## 9. Navegação para o trecho — certa, com um ajuste no fim

Sua conclusão está correta e é o desenho atual (círculo, não alfinete): o raio
cresce conforme a informação envelhece, e o app diz "vaga provável neste
trecho".

**O ajuste:** a rota não deve terminar na coordenada, e sim **40 a 60 m antes,
no sentido do tráfego**, entregando para uma instrução falada — *"olhe à
direita nos próximos 50 metros"*. O motorista precisa dos olhos no meio-fio
naquele momento, não na tela. Terminar a navegação sobre o ponto faz ele passar
direto e ter que dar a volta.

## 10. Lado da rua — viável, e o melhor sinal não está na sua lista

Sua lista de sinais (trajetória, direção, mapa, geometria, distância à linha da
via) está certa, mas começa pelo mais fraco: **a distância do ponto à linha da
rua não resolve.** O erro lateral do GPS em canyon urbano é de 5 a 15 m, e uma
rua tem 8 a 12 m de largura — o erro é do tamanho da coisa que se quer medir.

**O sinal forte é o rumo, não a posição.** Em trânsito pela direita, o carro
estaciona junto ao meio-fio à direita *do sentido em que ele está apontado*.
Então:

- **na saída:** o rumo dos primeiros metros do trecho veicular diz para onde o
  carro apontava; o meio-fio que ele ocupava é o da direita desse rumo;
- **na chegada:** o rumo dos últimos segundos antes de parar dá a mesma
  informação;
- **de reforço:** os primeiros 10 a 20 m da caminhada, que costumam ir na
  direção da calçada do lado onde o carro parou.

Rumo é robusto a erro lateral de GPS de um jeito que coordenada não é. Isso
transforma um problema difícil em um problema tratável.

**Onde eu discordo é na prioridade.** Em rua estreita ou de mão única, errar o
lado custa pouco — você vê a vaga da outra faixa. Em avenida com canteiro
central, custa 500 m e uma conversão. Então não é para resolver em toda a
cidade: é para resolver **onde a geometria torna o erro caro**, e mostrar
"lado indefinido" no resto. Isso é v1.3, refinamento de um app que já funciona.

## 11 e 12. Histórico e previsão — mais importantes do que você colocou

São a mesma ideia em duas resoluções, e ambas estão certas. Mas você as tratou
como "evolução futura", e elas são a coisa que resolve o problema mais urgente
do projeto: **é a única função da sua lista que funciona com zero usuários
online.** Com dado aberto de rotativo, ela pode estar pronta *antes* do
primeiro usuário. Eu subiria para v1.2.

**Um aviso sobre a lista de variáveis.** Dia da semana, horário, região,
feriados, eventos, comércio, escolas, restaurantes, escritórios — são nove
famílias de variável. Com o volume de dado do primeiro ano, um modelo assim
decora em vez de aprender. Comece com **três**: célula, dia da semana, hora.
Elas explicam a maior parte da variação de demanda por estacionamento. Cada
variável nova entra só depois de medir que ela melhora a previsão.

**E a maquete promete demais.** "82% / 61% / 43%" sugere uma precisão que o
dado não sustenta. O honesto é faixa — "entre 55% e 70%" — ou três níveis. Uma
barra com dois dígitos convida o usuário a confiar no segundo dígito.

## 13. "Vou sair daqui" — a ideia mais promissora e a mais perigosa

**Por que é tão boa:** é o único sinal da sua lista que dá **antecedência**.
Uma vaga que abriu há 40 segundos é quase inútil para quem está a 2 km — não
dá tempo. Cinco minutos de aviso mudam o jogo. Isso tira o produto do reativo e
o coloca no preditivo, no nível do indivíduo.

**Por que é perigosa:** é uma promessa feita por um ser humano sobre a própria
agenda, e gente erra isso sistematicamente. "Saio em 10 minutos" vira 25
minutos com frequência alta. E o dano não é simétrico: quem foi atrás de um
ponto verde comum e não achou nada perdeu 1 minuto; quem foi atrás de um
🟣 "vaga em breve" **esperou parado na rua**, atrapalhando o trânsito, para
nada. Esse usuário não volta.

Quanto isso custa já é mensurável no simulador. Com os motoristas de app
seguindo os pontos do mapa:

| Ocupação | Seguiu um ponto verde e não achou vaga |
|---:|---:|
| 92% | 10% a 18% |
| 97% | **29% a 36%** |

A frustração **cresce justamente onde o app é mais necessário**. E isso é com
pontos já confirmados por sensor — uma promessa manual de saída futura seria
pior.

**O desenho que salva a ideia:** não anunciar vaga futura *como vaga*.
Anunciar como **fila com confirmação**:

```
  João sinalizou saída em ~10 min, a 300 m do seu destino.
  [ Quero ser avisado quando confirmar ]   ← 1 pessoa na fila
```

O ponto verde só nasce quando a detecção automática confirma que o carro saiu.
O sinal manual não cria vaga: ele decide **quem é avisado primeiro**. Assim
você mantém os dois ganhos (antecedência e atribuição) e elimina a mentira.

Um detalhe social: o app nunca deve dizer *qual* carro vai sair. Transformar
isso em negociação entre estranhos na rua acaba mal.

## 14. Reputação — problema certo, mecanismo errado

Sua ressalva ("evitaria transformar em competição agressiva") está certa, e eu
iria além: **pontuação visível é a solução errada para o problema que você
descreve.** O problema é qualidade de dado. A solução é **peso, não placar**.

- Cada aparelho carrega um peso interno de confiabilidade, derivado de quantas
  das suas detecções foram corroboradas por outros. Esse peso multiplica a
  confiança dos eventos dele. **Invisível.**
- Um score visível ("⭐ 98%") cria incentivo para farmar o score — e farmar
  score é exatamente a fonte de dado ruim que a reputação existia para evitar.
- Tem um custo escondido: reputação pública durável exige **conta de usuário**,
  e conta de usuário derruba o desenho de privacidade que é a maior defesa do
  produto. Pagar privacidade para comprar reputação é péssimo negócio aqui.

A única recompensa visível que vale a pena: **quem contribui recebe as ofertas
de vaga antes**. Não pode ser farmada, porque vem de detecção automática, e o
prêmio é a própria vaga.

---

## O que falta na sua lista

Três buracos, em ordem de gravidade:

1. **A corrida pela vaga.** Os itens 6, 8 e 13 passam perto e nenhum a nomeia.
   Medido acima: 10% a 36% de viagens frustradas. É o problema que o app
   **cria**, e sem atribuição ele piora conforme a base cresce.
2. **Legalidade da vaga.** Nada na sua lista impede o app de mandar alguém
   parar em cima de uma faixa de pedestre ou de um hidrante. Um ponto verde
   ilegal é pior que ponto nenhum: é uma multa causada pelo app.
3. **Ninguém pode olhar a tela.** Todas as nove ideias pressupõem um motorista
   lendo o celular enquanto dirige. Acima de 10 km/h, tudo tem que ser voz.

## Sobre as medições acima

O simulador tem dois limites que impedem ler os números como previsão:

- **Os tempos absolutos de procura são otimistas demais** (33 s com ocupação
  de 92%). No grid sintético toda rua tem vaga e o motorista acha uma a 20 m;
  num centro real, circular por vaga leva de 5 a 15 minutos. Os números valem
  como **comparação entre cenários**, não como estimativa de campo.
- A taxa de frustração e a diferença entre "com e sem chegadas" são robustas,
  porque comparam o mesmo mundo com uma variável trocada.

Reproduzir: `npm run cobertura`, e as opções `cruising`, `followMap` e
`ignoreArrivals` de `runCity` em `packages/core/src/city.ts`.
