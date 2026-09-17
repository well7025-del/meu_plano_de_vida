# Como a detecção funciona

## A premissa

Duas assinaturas de movimento, e só duas, interessam:

**Saída (vaga liberada)**

```
  ~1,4 m/s por um tempo   →   parado alguns segundos   →   >4,2 m/s sustentado
  (a pessoa caminha)          (destrava, entra)            (o carro anda)
```

Se alguém caminhava e passou a se deslocar em velocidade de carro, o carro
estava parado onde a caminhada terminou. Aquele lugar acabou de vagar.

**Chegada (vaga ocupada)**

```
  >4,2 m/s   →   ~0 m/s por ≥90 s   →   ~1,4 m/s se afastando ≥40 m
  (dirigindo)    (estacionou)           (saiu do carro e foi embora)
```

## O problema real não é detectar — é não detectar errado

A assinatura acima é fácil de encontrar. O trabalho é rejeitar tudo que se
parece com ela:

| Situação | Por que engana | Como é rejeitada |
|---|---|---|
| Semáforo, fila de trânsito | carro → parado → carro | parada precisa durar ≥ 90 s **e** ser seguida de caminhada; voltar a dirigir cancela |
| Atravessar a rua correndo | pico de velocidade no meio da caminhada | filtro de mediana + faixa ambígua de 10–15 km/h onde o classificador não vota |
| Embarque/desembarque de ônibus | a pé → veículo → a pé, idêntico | contexto do mapa: ponto de ônibus a <30 m derruba a confiança a 60% |
| Passageiro de Uber/carona | a pé → veículo, sem carro nenhum envolvido | deslocamento mínimo de 200 m não resolve; resolvido por reforço (ver abaixo) e pelo histórico do próprio aparelho |
| Sair da garagem de casa | assinatura perfeita de vaga de rua | zona silenciosa cadastrada pelo usuário; confiança vai a zero |
| Estacionamento de shopping | assinatura perfeita | polígono de estacionamento privado no mapa offline: confiança × 0,1 |
| Ruído de GPS entre prédios | saltos de dezenas de metros parado | amostras com precisão pior que 50 m são descartadas; posição vem da mediana |
| App abrindo com a pessoa já dirigindo | não há caminhada observada antes | sem trecho a pé anterior, nenhuma saída é emitida |

Essa última linha foi um bug encontrado nos testes: a primeira versão emitia
uma "vaga liberada" fantasma toda vez que o app iniciava com o usuário em
movimento. Está coberta pelo teste `estacionamento privado derruba a confiança`.

## Confirmação atrasada

Nenhum evento é emitido no instante em que acontece. A saída só é publicada
depois que o trecho de carro provou ser deslocamento real (≥ 45 s **e** ≥ 200 m);
a chegada, depois que a pessoa se afastou ≥ 40 m a pé.

Isso custa uns dois minutos de atraso na saída — e é o que elimina a maior
fonte de lixo. O evento carrega dois horários: `t` (quando a vaga vagou) e
`confirmedAt` (quando o motor teve certeza). A probabilidade decai a partir de
`t`, não de `confirmedAt`, senão o mapa mentiria para mais.

## Ancoragem no tempo

O modo só muda depois de a histerese se sustentar por 15–20 s. A 30 km/h, o
carro já andou 150 m nesse intervalo. Por isso o detector guarda uma janela de
amostras recentes e **volta no tempo** para ancorar o evento no instante em que
o trecho começou, não em quando ele foi confirmado. Sem isso, o ponto verde
aparecia uma quadra à frente da vaga — foi o segundo bug pego pelos testes
(`o ponto ancora da saida fica onde o carro estava`).

Erro mediano do ponto na simulação: **20 m**. P90: **26 m**.

## Confiança

Cada evento sai com um número de 0 a 1, composto por:

```
  0,25  base
+ 0,20 × duração da caminhada adjacente (satura em 50 s)
+ 0,20 × distância dirigida (satura em 600 m)
+ 0,20 × qualidade do GPS no ponto âncora
+ 0,15 × duração da parada (só para chegadas, satura em 4,5 min)
+ 0,20  se este mesmo aparelho estacionou ali antes  ← o sinal mais forte
```

e depois multiplicada pelas penalidades de contexto (estacionamento privado,
via expressa, ponto de ônibus, proibido estacionar). Abaixo de 0,45 o evento é
descartado silenciosamente.

O reforço `bonus:own_prior_arrival` merece destaque: quando o app detectou a
chegada e detecta a saída no mesmo ponto, não há ambiguidade nenhuma — é o
mesmo carro, é a mesma vaga, e a pessoa não era passageira de ninguém. É de
graça e vale mais que qualquer heurística.

## Do evento ao ponto verde

No servidor, cada célula de 40 m acumula:

```
  λ = Σ (confiança × decaimento)  das saídas
    − Σ (confiança × decaimento)  das chegadas
    + oferta de base do histórico

  P(existe ao menos uma vaga livre) = 1 − e^−λ
```

O decaimento é exponencial com meia-vida de 12 minutos: uma vaga anunciada há
12 min vale metade; há 24 min, um quarto. Em rua de comércio a meia-vida real é
menor, em rua residencial à noite é maior — o valor é aprendido por região a
partir do intervalo medido entre saída e próxima chegada na mesma célula.

Poisson foi escolhido por ser explicável: dá para dizer ao usuário "dois carros
saíram daqui nos últimos 6 minutos" em vez de mostrar uma porcentagem que
ninguém sabe de onde veio. E é calibrável: se o botão "não tinha nada" for
apertado em 40% dos casos, é a meia-vida que está errada, e dá para consertar
por região sem mexer em mais nada.

## O que a rua quebra

Honestamente, o que esta versão ainda não resolve:

1. **Passageiro de ônibus em ponto sem mapa.** A penalidade depende de ter o
   ponto mapeado. Sem isso, cada viagem de ônibus vira uma vaga falsa no
   ponto de embarque. Correção real: casar a trajetória com as linhas da GTFS
   da cidade — se o trecho seguiu uma linha de ônibus por 3 paradas, não foi
   carro. Fica para a v1.1.
2. **Vaga em fila dupla.** Assinatura idêntica à de uma vaga legítima.
   Precisa de camada de "aqui não pode" vinda do mapa e da denúncia dos
   usuários.
3. **Garagem coberta.** GPS morre na rampa; o evento sai com o ponto na
   entrada da garagem. Mitigação: o buraco de sinal em si é sinal — perda
   abrupta de precisão logo antes de parar sugere estrutura fechada.
4. **Vaga que já estava ocupada quando o ponto apareceu.** Entre a saída de um
   carro e a chegada de outro que não usa o app, o mapa fica mentindo até
   alguém apertar "não tinha nada". É o limite fundamental do método — e o
   motivo de o botão de feedback ser tão proeminente na interface.
5. **Bicicleta e patinete** caem na faixa ambígua de propósito, mas um trecho
   longo de bicicleta em ciclovia rápida pode passar por carro.
