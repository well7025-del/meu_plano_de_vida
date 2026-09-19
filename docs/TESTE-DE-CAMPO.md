# Teste de campo com amigos

Guia para colocar o Vagas no ar hoje e testar na rua. Leva uns 15 minutos.

## 1. Subir o app

> **Sem terminal?** Há um caminho só de cliques, pelo GitHub, em
> [PUBLICAR.md](PUBLICAR.md). Comece por ele se a Opção A não funcionar.

### Opção A — Cloudflare (recomendada: endereço fixo, não depende do seu PC)

O banco D1 já está criado e com o esquema aplicado (`vagas`,
`1263f2dd-ca41-48bc-810b-02f6d25f41d8`), e o `wrangler.toml` já aponta para
ele. Faltam dois comandos, no seu computador:

```bash
npx wrangler login     # abre o navegador, você autoriza
npm run publicar       # compila, monta os arquivos e publica
```

No fim, o wrangler imprime o endereço:

```
https://vagas.<seu-subdominio>.workers.dev
```

**Esse é o endereço definitivo do teste.** Fica no ar 24 h por dia, de graça no
plano gratuito, servido do data center mais próximo de cada pessoa. Para
publicar uma correção depois, é `npm run publicar` de novo.

Se o `wrangler login` não abrir o navegador (máquina remota, WSL), use um token
de API em vez disso: crie em *My Profile → API Tokens* com o modelo **Edit
Cloudflare Workers** e exporte `CLOUDFLARE_API_TOKEN=...` antes do comando.

### Opção B — seu computador + túnel (para desenvolver e ver logs)

```bash
npm run testar
```

Isso instala o que falta, compila o motor e sobe o servidor. Se você tiver o
`cloudflared` instalado, ele já abre um endereço HTTPS público e imprime algo
assim:

```
https://algo-aleatorio-aqui.trycloudflare.com
```

**Esse é o endereço para mandar no grupo.** Enquanto o terminal estiver aberto,
o teste está no ar.

Se não tiver o `cloudflared`, o script te diz como instalar (`brew install
cloudflared` no Mac, `winget install --id Cloudflare.cloudflared` no Windows).
Não precisa de conta.

> **Por que não dá para mandar o `localhost`:** o navegador só libera o GPS em
> conexão segura (HTTPS), e o `localhost` do seu computador não é o do celular
> do seu amigo. O túnel resolve os dois problemas de uma vez.

Há também um `Dockerfile` na raiz, se você preferir Fly.io, Render ou Railway
em vez da Cloudflare.

> **Qual usar:** a Opção A para o teste com os amigos (endereço fixo, sempre no
> ar). A Opção B enquanto você mexe no código, porque o servidor recarrega e
> os logs aparecem no terminal.

## 2. Instalar no celular

Cada pessoa abre o endereço no celular e:

- **Android (Chrome):** menu ⋮ → *Adicionar à tela inicial*
- **iPhone (Safari):** botão de compartilhar → *Adicionar à Tela de Início*

Instalado, ele abre em tela cheia, sem barra de navegador. Depois é só tocar em
**Permitir localização e começar**.

## 3. A limitação que você precisa explicar para todo mundo

> **O app precisa ficar aberto, com a tela ligada.**

O navegador congela o JavaScript quando a tela apaga — e aí a detecção para.
Para o teste funcionar:

- celular no **suporte do painel**, **no carregador**, app aberto;
- o app tenta impedir a tela de apagar sozinho, mas nem todo celular deixa;
- quando ele percebe que ficou congelado, escreve isso no Diagnóstico.

Isso é limitação do navegador, não do método. A versão nativa (`apps/mobile`)
roda em segundo plano com a tela apagada — mas exige publicar nas lojas, o que
não faz sentido antes de saber se a detecção funciona na sua rua.

## 4. O roteiro do teste

Peça para cada pessoa fazer, pelo menos uma vez:

1. Abrir o app **antes** de sair a pé em direção ao carro.
2. Caminhar até o carro (pelo menos 1 minuto de caminhada).
3. Entrar, ligar e dirigir por **pelo menos 3 minutos**.
4. Estacionar na rua, ficar parado uns 2 minutos, e **sair caminhando** por
   pelo menos 1 minuto.

Se funcionou, o Diagnóstico mostra:

```
🟢 vaga liberada · confiança 88%
🔴 vaga ocupada  · confiança 76%
```

E quem estiver por perto vê o ponto aparecer no radar.

## 5. O que anotar — a parte que decide o projeto

Antes de qualquer conclusão sobre o app, meça o problema. Para **cada** vez que
alguém procurar vaga, anote em uma planilha:

| Data/hora | Bairro | Chegou na região | Estacionou | Minutos procurando | Usou o app? | Achou pelo app? |
|---|---|---|---|---|---|---|

O número da coluna "minutos procurando" é a **linha de base**. Sem ele, daqui a
três meses não haverá como provar que o app ajudou — e essa prova é o que abre
a conversa com a prefeitura, com a operadora de zona azul e com investidor.

Também vale registrar:

- **falsos positivos:** o app disse "vaga liberada" e você não saiu de vaga
  nenhuma (saiu de garagem? era ônibus? era carona?);
- **falhas:** você saiu de uma vaga e o app não detectou;
- **pontos mentirosos:** foi até um ponto verde e não tinha nada — aperte o
  **✗** no app, que é exatamente para isso.

## 6. Mensagem pronta para mandar no grupo

> Oi! Estou testando um app que detecta sozinho quando uma vaga de rua fica
> livre — sem apertar nada, só pelo movimento do celular.
>
> Link: `<cole o endereço aqui>`
> No Android: menu do Chrome → Adicionar à tela inicial.
> No iPhone: compartilhar → Adicionar à Tela de Início.
>
> Como funciona: quando você caminha e depois entra no carro e dirige, ele
> conclui que uma vaga abriu ali e avisa quem está procurando. Quando você
> estaciona e sai a pé, ele marca a vaga como ocupada.
>
> **O que ele coleta:** só o ponto aproximado da vaga, o horário e o grau de
> confiança. Sem nome, sem conta, sem identificador do celular. Seu trajeto
> não sai do seu aparelho, e os dados somem do servidor em 1 hora.
>
> **Importante:** precisa ficar aberto com a tela ligada (celular no suporte e
> no carregador). É um teste, vai falhar às vezes — me conta quando falhar.
>
> Para sair, é só apagar o ícone.

## 7. Quando parar e o que concluir

Depois de uma ou duas semanas, três perguntas:

1. **A detecção funciona?** Taxa de acerto por pessoa: quantas saídas reais
   viraram evento. Abaixo de 70%, o problema está nos limiares — ajuste em
   `packages/core/src/types.ts` e rode `npm test`.
2. **Os pontos são verdadeiros?** Quantas vezes o ✗ foi apertado. Muito ✗
   significa meia-vida errada para a sua região (`halfLifeS` em
   `availability.ts`).
3. **Alguém achou vaga por causa do app?** Com 5 ou 10 pessoas, provavelmente
   não — e isso **não** é o fracasso do produto. A medição de
   [DADOS.md](DADOS.md) mostra que a utilidade só aparece com algumas centenas
   de pessoas no mesmo bairro. O que este teste responde é a pergunta anterior:
   *a detecção funciona no mundo real?* Se ela funciona, o resto é distribuição.

## Resolvendo problemas

| Sintoma | Causa provável |
|---|---|
| "Permissão negada" e não pede de novo | o navegador guardou o não. Configurações do site → Localização → Permitir |
| Nenhum evento depois de um trajeto completo | tela apagou no meio (veja o Diagnóstico) ou o trajeto de carro teve menos de 200 m |
| Velocidade sempre 0 | o celular não reporta velocidade; o app deriva da posição, mas precisa de GPS bom (menos de 25 m) |
| Precisão acima de 50 m o tempo todo | garagem, prédio alto ou GPS em modo economia. Amostras assim são descartadas de propósito |
| O ponto aparece na rua errada | erro normal do GPS urbano; anote e compare com o raio que o app mostra |
| Voz não fala | iPhone exige um toque na tela antes de liberar áudio — toque em "Procurar vaga" de novo |
