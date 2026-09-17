# Privacidade

Um app que precisa de localização em segundo plano 24 h por dia para funcionar
tem que ser radicalmente honesto sobre isso, ou não merece a permissão.

## O que sai do celular

Exatamente isto, e nada mais:

```json
{ "kind": "departure", "lat": -23.561300, "lon": -46.656500,
  "t": 1768478400000, "confidence": 0.87 }
```

Sem nome. Sem e-mail. Sem conta. Sem ID do aparelho. Sem ID de publicidade.
Sem token que ligue dois eventos à mesma pessoa.

## O que nunca sai

- a trajetória (ela existe só na memória do processo, em uma janela de 600
  amostras, e é descartada);
- os horários exatos em que você se moveu — o envio é em lote, com atraso
  aleatório de até 2 minutos;
- qualquer coisa dentro de uma zona silenciosa que você cadastrou;
- qualquer evento com confiança abaixo do corte.

## Decisões concretas

| Decisão | Efeito |
|---|---|
| Detecção roda no dispositivo | o servidor nunca vê uma sequência de posições |
| Sem autenticação nas rotas de evento | não há a quem associar o dado, nem por engano |
| Coordenada arredondada a 6 casas (~11 cm) no cliente e no servidor | corta a falsa precisão que permitiria fingerprinting |
| Agregação em células de 40 m | o ponto exibido é a média de eventos, não a posição de uma pessoa |
| Atraso aleatório no envio | quebra a correlação entre o horário do envio e o do movimento |
| Retenção de 1 hora | depois disso o evento não tem valor operacional nenhum; manter seria coleta sem finalidade |
| Histórico só agregado | `(célula, dia, hora) → média`; nenhum evento individual sobrevive |

## Zonas silenciosas

O usuário marca a garagem de casa, o trabalho, a casa da mãe. Dentro do raio,
nenhum evento é gerado — a supressão acontece **antes** do evento existir, não
no servidor.

Isso também melhora a qualidade do dado: a saída da própria garagem tem a mesma
assinatura de movimento de uma vaga de rua e anunciaria uma vaga inexistente.
Privacidade e precisão puxam para o mesmo lado aqui, o que é raro e conveniente.

## LGPD

Com o desenho acima, o evento publicado não é dado pessoal — não identifica nem
torna identificável uma pessoa natural. Isso não dispensa:

- **consentimento específico e destacado** para localização em segundo plano,
  com a explicação em português antes do diálogo do sistema, não depois;
- **desligar e continuar funcionando**: quem desativa a contribuição continua
  vendo o mapa. Coleta não pode ser pedágio;
- **relatório de impacto** antes do lançamento, porque monitoramento contínuo
  de localização é tratamento de alto risco mesmo quando anonimizado;
- **revisão do risco de reidentificação**: em rua de baixíssimo movimento, um
  único evento pode apontar para uma única pessoa. Mitigação implementada:
  supressão de células com pouquíssimo suporte fica pendente e está marcada no
  roadmap como pré-requisito de lançamento.

## O que eu não faria

Vender localização para terceiros, mesmo agregada, mesmo "anonimizada". É o
modelo de negócio que destruiu a confiança em toda uma categoria de apps, e
seria especialmente indefensável aqui — as pessoas estão entregando onde
moram e onde trabalham, todo dia, sem apertar nada.
