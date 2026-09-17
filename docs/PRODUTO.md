# Produto: o que eu acrescentaria, e em que ordem

O mapa de pontinhos verdes é o começo, não o produto. Sozinho ele tem dois
defeitos que aparecem na primeira semana de uso real:

1. **ele cria uma corrida.** Cinco motoristas veem o mesmo ponto verde e
   convergem para a mesma quadra. Quatro se frustram, e a frustração é *causada
   pelo app*;
2. **ele é inútil no exato momento em que é mais necessário.** Quem está
   procurando vaga está dirigindo. Olhar um mapa dirigindo é perigoso e ilegal.

Quase tudo abaixo existe para resolver essas duas coisas.

---

## v1 — o que já está construído aqui

| | |
|---|---|
| Detecção automática de saída e chegada | sem botão, sem atrito |
| Mapa com pontos por probabilidade e idade | círculo, não alfinete: a incerteza fica visível |
| "Achei" / "não tinha nada" | calibra o modelo e apaga o ponto morto na hora |
| Onde deixei meu carro | cai de graça da detecção de chegada |
| Zonas silenciosas | privacidade e qualidade do dado no mesmo controle |

---

## v1.1 — tornar utilizável dirigindo

**1. Navegação por probabilidade, não por destino.**
A função mais importante do app inteiro, e a que nenhum concorrente tem. O
motorista não quer um alfinete: quer uma **rota de busca**. Em vez de "vire à
direita em 200 m para chegar ao ponto verde", o app calcula o trajeto que passa
pelas ruas de maior probabilidade acumulada entre a posição atual e o destino,
e vai reordenando conforme os pontos mudam. É um problema de caminho de máximo
valor esperado, e resolve a corrida: cada motorista recebe uma rota diferente.

**2. Só voz enquanto o carro anda.**
Acima de 10 km/h a tela trava e o app só fala: *"vaga provável à direita, 150
metros"*. Widget, Android Auto e CarPlay pela mesma razão.

**3. Aviso ao chegar perto do destino.**
Você está a 300 m do restaurante, alguém acabou de sair a 80 m dele. Uma
notificação, no momento certo. É o caso de uso com maior valor por interrupção
do app inteiro.

**4. Atribuição em vez de anúncio.**
Quando uma vaga abre e há três pessoas procurando por perto, ela é **oferecida
a uma** por 90 segundos, com direito de recusa. Quem recusa ou não chega devolve
ao próximo. Sem isso, o app escala mal por construção: quanto mais gente usa,
pior ele fica.

---

## v1.2 — saber o que a vaga custa e se ela é legal

**5. Camada de regras da via.** Zona azul, rotativo, horário de carga e
descarga, proibido estacionar, faixa de pedestre, hidrante, garagem, ponto de
ônibus. Um ponto verde em cima de uma faixa é pior que ponto nenhum: é uma
multa que o app causou. Vem do OSM + dados abertos da prefeitura.

**6. Pagamento do rotativo no próprio app**, com aviso de vencimento. O
lembrete de "seu tempo acaba em 10 min" é sozinho um motivo para abrir o app
todo dia — e a integração com as operadoras de zona azul é a via mais direta de
monetização sem tocar em dado de localização.

**7. Alerta de estacionamento irregular.** Detectou chegada em cima de uma
restrição: *"aqui é proibido das 7h às 9h"*, na hora em que a pessoa sai do
carro, não depois da multa.

**8. Previsão, não só tempo real.** *"Nesta rua, terça às 19h, a chance
histórica é de 65%"*. Funciona mesmo onde não há nenhum usuário do app naquele
instante, o que cobre o buraco da partida a frio. Modelo por
`(célula, dia da semana, hora)`, que o `baselineLambda` do motor já aceita.

**9. Vagas acessíveis.** PCD e idoso identificadas e filtráveis. Quem precisa
delas tem o problema de estacionamento multiplicado, e são as vagas mais fáceis
de mapear, porque são fixas e oficiais.

---

## v1.3 — segurar quem já usa

**10. Reputação útil, não pontinhos.** Quem contribui com detecções confirmadas
recebe as ofertas de vaga **antes**. É a única gamificação que faz sentido aqui:
a recompensa é a própria vaga.

**11. Grupos.** Família, equipe, frota. "Onde está o carro da empresa", "meu
filho estacionou onde?", com consentimento explícito de cada participante.

**12. Estacionamento pago como plano B.** Probabilidade baixa na região inteira
→ o app oferece o estacionamento mais próximo com preço e vagas livres. Resolve
o problema do usuário quando o produto principal não tem resposta, e é a segunda
linha de receita (comissão por indicação).

**13. Modo passageiro.** Uber, ônibus, carona. Detectado pelo padrão (embarque e
desembarque em pontos diferentes, sem carro próprio no histórico) ou marcado
pelo usuário, para não poluir a rede com vagas falsas.

---

## v2 — o que só faz sentido com escala

**14. Sinal direto do carro.** As APIs de carro conectado (Fiat, VW, Renault,
Tesla) entregam ignição, câmbio em P e porta aberta. Isso substitui toda a
inferência por um fato, e leva a precisão de "muito boa" para "exata" em quem
tem carro compatível — que, de quebra, vira a fonte de treino para calibrar a
inferência dos outros.

**15. Contagem de vagas por visão computacional.** Dashcam ou celular no
suporte, processando na borda, contando espaços vazios no meio-fio. Muda a
natureza do dado: em vez de "alguém saiu daqui", passa a ser "há 3 vagas neste
quarteirão agora". É caro, exige consentimento reforçado e só se paga em alta
densidade — por isso é v2 e não v1.

**16. Painel para a prefeitura.** Onde falta vaga, em que horário, quanto tempo
as pessoas circulam procurando. Estudos clássicos de Donald Shoup atribuem
cerca de 30% do tráfego em centros urbanos à procura por vaga; medir isso em
tempo real é uma ferramenta de política pública que hoje ninguém tem. Dados
agregados por quarteirão e por hora — nunca por pessoa — e é a terceira linha
de receita (B2G), a mais alinhada com o propósito do app.

---

## O que eu deliberadamente **não** faria

- **Reserva de vaga pública.** Vender antecipadamente o direito a um espaço
  público é indefensável, gera conflito na rua e, em várias cidades, é ilegal.
  A atribuição temporária da v1.1 é outra coisa: ela distribui uma informação,
  não aluga o asfalto.
- **Feed social, chat, foto da vaga.** Cada campo livre é um canal de abuso e
  um custo de moderação que um app de utilidade não sustenta.
- **Vender localização, mesmo agregada.** Ver [PRIVACIDADE.md](PRIVACIDADE.md).
- **Lançar em várias cidades ao mesmo tempo.** Densidade vence alcance. Um
  bairro que funciona vale mais que dez capitais com o mapa vazio.

---

## Como eu mediria se está funcionando

Uma métrica norte, e ela não é DAU:

> **Tempo entre entrar na região do destino e desligar o carro estacionado.**

O app mede isso sozinho, com a mesma detecção que já faz: começa quando a
pessoa entra num raio de 500 m do destino, termina no evento de chegada. Se
esse número não cai para quem usa o app comparado a quem não usa, nada mais
importa — nem a precisão do motor, nem o tamanho da base.

Métricas de apoio:

| Métrica | Alvo inicial | Por que importa |
|---|---|---|
| Taxa de confirmação ("achei" / total de avisos) | > 60% | mede a honestidade do mapa; é a calibração da meia-vida |
| Cobertura por quarteirão no bairro piloto | > 40% dos quarteirões com evento na última hora | abaixo disso o mapa parece quebrado |
| Bateria por dia | < 1,5% | acima disso o app é desinstalado, ponto final |
| Eventos por usuário ativo por dia | ≈ 2 | muito abaixo = detecção falhando; muito acima = falso positivo |
| Retenção D30 | > 25% | utilidade real, não curiosidade |

## O risco que eu vigiaria de perto

O app **causa** trânsito se acertar pela metade: manda gente para a mesma
quadra, ela não acha vaga, e circula mais do que circularia sem o app. A
diferença entre uma ferramenta que reduz o tráfego de procura em 30% e uma que
o aumenta está inteiramente na atribuição (item 4) e na calibração da
probabilidade. É por isso que o botão "não tinha nada" é tão proeminente na
interface, e por isso a taxa de confirmação é métrica de primeira linha e não
de painel interno.
