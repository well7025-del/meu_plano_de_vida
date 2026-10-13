# Mercadinho Fácil — como publicar e vender

O app foi feito para ser vendido com **pagamento único** e **sem servidor**:
não há custo mensal de infraestrutura por cliente, porque cada comerciante
guarda os próprios dados no próprio celular.

## 1. Caminho recomendado: app pago na Play Store

É o único jeito de ter "pagamento único pelo aplicativo" **sem precisar de
servidor de licença**: o Google cobra, entrega e impede a cópia.

| Item | Como é |
|---|---|
| Conta de desenvolvedor | Taxa única de US$ 25 em play.google.com/console |
| Comissão do Google | 15% (até US$ 1 milhão por ano de receita) |
| Preço | Você escolhe; o Google converte e cobra em reais, aceita Pix e cartão |
| Atualizações | Grátis para quem comprou; sobem pelo mesmo app |
| Reembolso | O Google cuida (até 48 h após a compra) |

Passos:

1. **Criar a chave de assinatura** (uma vez, guarde em 2 lugares; sem ela não
   dá para atualizar o app nunca mais):
   ```bash
   keytool -genkeypair -v -keystore mercadinho.jks -alias mercadinho \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. **Cadastrar a chave no GitHub** (Settings > Secrets and variables >
   Actions > New repository secret):
   - `MERCADINHO_KEYSTORE_B64` — o arquivo em base64 (`base64 -w0 mercadinho.jks`)
   - `MERCADINHO_KEYSTORE_SENHA`, `MERCADINHO_CHAVE_ALIAS`, `MERCADINHO_CHAVE_SENHA`
3. Rodar o workflow **Mercadinho Fácil - APK** e baixar o `.aab` do artefato.
4. Na Play Console: criar o app como **pago**, subir o `.aab` em "Teste
   interno" primeiro, depois "Produção".
5. Preencher a **Segurança dos dados**: o app *não coleta nem compartilha
   dados* (não tem permissão de internet). Permissões usadas: microfone
   (comandos de voz), câmera (código de barras e fotos), Bluetooth
   (impressora).
6. **Política de privacidade**: a Play exige um link. Um texto curto basta
   (modelo no fim deste documento) publicado em qualquer página.

Antes de publicar, troque o `versionCode`/`versionName` em
`apps/mercadinho/app/build.gradle.kts` a cada versão nova.

## 2. Alternativa: vender o APK por fora (Hotmart, Kiwify, site próprio)

Funciona, mas tem dois custos que a Play Store resolve:

- **Cópia**: o APK pode ser repassado. Evitar isso exige um sistema de
  licença (chave por cliente). Fazer isso de forma confiável sem servidor é
  possível com chaves assinadas digitalmente que o app confere offline — dá
  para acrescentar depois, mas não está nesta versão.
- **Instalação**: o cliente precisa liberar "instalar apps de fontes
  desconhecidas", que assusta justamente o público que tem pouca
  familiaridade com tecnologia.

Se quiser os dois canais, publique na Play Store e use a Hotmart/Kiwify
para vender um **pacote com treinamento** (vídeo ensinando a usar) que
inclui o link da Play Store.

## 3. Versão de teste (para o cliente experimentar antes de pagar)

O mesmo código gera duas versões. O build do GitHub publica cada uma num
artefato separado:

| | Versão de teste | Versão completa |
|---|---|---|
| Artefato do Actions | `mercadinho-teste` | `mercadinho-completo` |
| Nome no celular | Mercadinho Fácil Teste (ícone laranja) | Mercadinho Fácil (ícone verde) |
| Pacote | `br.com.mercadinhofacil.teste` | `br.com.mercadinhofacil` |
| Limites | 15 produtos, 5 clientes, 50 vendas | nenhum |
| Funções | todas (voz, Pix, impressão, catálogo, relatórios, dicas, backup) | todas |

- Ao bater um limite, aparece a janela **"Limite da versão de teste"** com
  o botão **Comprar a versão completa**. Na tela inicial, uma faixa mostra
  quanto já foi usado ("Produtos: 8 de 15…") com o mesmo botão.
- O limite de vendas conta também as canceladas, para não dar para
  "renovar" o teste cancelando vendas.
- As duas versões podem ficar instaladas juntas. Para **levar os dados do
  teste para a completa**: no teste, Ajustes > Backup > "Enviar o último
  backup"; na completa, Ajustes > Backup > "Restaurar de um arquivo". A
  versão de teste não restaura backups (senão daria para furar o limite).
- **Link do botão de compra**: cadastre no GitHub em Settings > Secrets and
  variables > Actions > aba *Variables* > `MERCADINHO_LINK_COMPRA` (por
  exemplo o link da Play Store, da Hotmart/Kiwify ou `https://wa.me/55DDDNUMERO`).
  Sem a variável, o botão abre a página da versão completa na Play Store.
- Os limites ficam em `apps/mercadinho/app/build.gradle.kts`, no bloco
  `create("teste")` (`LIMITE_PRODUTOS`, `LIMITE_CLIENTES`, `LIMITE_VENDAS`).

**Como distribuir o teste**

- **Play Store** (recomendado): publique o `.aab` do teste como um app
  **gratuito** separado ("Mercadinho Fácil Teste") e a versão completa como
  app pago. O cliente instala o grátis e, quando gostar, compra o pago.
- **Direto pelo WhatsApp/site**: mande o arquivo
  `mercadinho-facil-TESTE.apk`. O cliente precisa autorizar "instalar apps
  desconhecidos" no celular.

Atenção honesta: num APK que circula livremente, alguém com conhecimento
técnico consegue desmontar e tirar o limite. Para o público deste app isso é
raro; a proteção de verdade da versão completa é vendê-la pela Play Store.

## 4. Preço e posicionamento

Referências do mercado brasileiro (apps de gestão para pequeno comércio):
os concorrentes populares cobram **mensalidade** de R$ 30 a R$ 90 e exigem
internet e cadastro. O argumento de venda deste app é o oposto:

- **paga uma vez, é seu**: sem mensalidade, sem conta, sem internet;
- **fala com o app**: para quem tem dificuldade de digitar;
- **fiado e Pix** resolvidos, que é a dor real da mercearia de bairro;
- **seus dados não saem do celular**.

Faixas que fazem sentido: R$ 49,90 a R$ 129,90 pagamento único. Um teste
simples: começar mais alto, oferecer cupom de lançamento e medir.

## 5. O que dizer com honestidade ao vender

- O comprovante é **sem valor fiscal** (está impresso nele). O app não emite
  NFC-e/cupom fiscal; quem é obrigado a emitir nota precisa de um emissor à
  parte.
- O app **não acessa a conta bancária**: o Pix é gerado no padrão do Banco
  Central, mas a confirmação de que o dinheiro caiu é feita pelo comerciante
  olhando o app do banco (o app deixa o Pix como "pendente" até lá).
- A voz funciona sem internet quando o celular tem o reconhecimento offline
  de português (padrão nos Android recentes; nos antigos, baixar o pacote
  "Português (Brasil)" nas configurações de voz do Google).
- Os dados ficam só no celular: o backup é responsabilidade do comerciante.
  O app faz backup diário automático no celular e lembra de tirar uma cópia
  para fora dele.

## 6. Antes de lançar — testar com 3 a 5 mercearias reais

1. Instalar o APK de teste (artefato `mercadinho-apk`, arquivo `app-debug.apk`).
2. Cadastrar 20 produtos que mais saem e usar 1 semana.
3. Anotar: frases de voz que o app não entendeu (são ouro: viram regra nova
   em `core/.../voz/Interpretador.kt` e um teste em `InterpretadorTest.kt`),
   modelo da impressora usada, telas onde a pessoa travou.

## Modelo de política de privacidade

> **Mercadinho Fácil — Política de Privacidade**
>
> O Mercadinho Fácil não coleta, não envia e não compartilha dados pessoais
> ou comerciais. Todas as informações cadastradas (produtos, vendas,
> clientes, contas e configurações) ficam armazenadas apenas no aparelho do
> usuário. O app não possui servidor e não usa a internet.
>
> O microfone é usado somente quando o usuário toca no botão de voz, para
> transformar a fala em comandos. A câmera é usada somente para ler códigos
> de barras e tirar fotos de produtos. O Bluetooth é usado somente para
> imprimir comprovantes.
>
> Os arquivos de backup são criados no aparelho e só saem dele se o próprio
> usuário decidir enviá-los. Dúvidas: [seu e-mail de contato].
