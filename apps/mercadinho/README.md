# Mercadinho Fácil

App Android para pequenos mercadinhos e mercearias: vendas, estoque, fiado,
Pix, caixa, catálogo e relatórios, **por voz**, com botões grandes e tudo
guardado só no celular. Sem servidor, sem conta e sem mensalidade.

## O que o app faz

| Área | O que tem |
|---|---|
| **Voz e assistente** | Fala natural em português ("vendi 2 arroz e uma coca no pix", "a Maria pagou 30", "quanto vendi hoje?", "o que está faltando?"). Mostra o que entendeu e só grava depois de confirmar. Responde em texto e em voz. |
| **Produtos e estoque** | Cadastro com preço, custo, categoria, unidade (un, kg, litro…), foto, apelidos para a voz, estoque mínimo. Leitura de código de barras e QR pela câmera. Entrada de mercadoria (com custo médio), perdas e histórico. |
| **Vendas** | Carrinho com vários produtos, itens por peso, item avulso, desconto, troco, Dinheiro/Pix/Cartão/Fiado. Comprovante como imagem ou texto no WhatsApp e impressão em impressora térmica Bluetooth (58 e 80 mm, com QR do Pix). |
| **Catálogo** | Gera catálogo com fotos, nomes, descrição e preço em **imagens**, **PDF** ou **texto** para mandar no WhatsApp. O cliente não precisa instalar nada. |
| **Contas** | Dinheiro, Pix e Cartão já criadas; pode criar outras. Cada venda cai na conta certa. Despesas, entrada do dono, retirada do dono, transferências (não contam como receita nem despesa), saldo, extrato e fechamento de caixa com diferença. |
| **Pix** | Cadastro de titular, banco e chave (valida CPF, CNPJ, celular, e-mail e aleatória). Gera "Pix Copia e Cola" e QR Code no padrão do Banco Central, com valor. Pix fica **pendente** até alguém confirmar que caiu. |
| **Fiado** | Clientes, dívida, limite, pagamento parcial, histórico e cobrança pelo WhatsApp com Pix. |
| **Relatórios** | Hoje, ontem, semana, mês e mês passado: vendas, CMV, lucro bruto, despesas, lucro estimado, ticket médio, formas de pagamento, fluxo de caixa, mais vendidos, maiores margens e margens baixas. |
| **Dicas** | Análise do negócio no celular: produtos em falta ou acabando, vendendo no prejuízo, margem apertada, mercadoria parada, vendas caindo, despesas altas, fiado atrasado, Pix esperando, melhor/pior dia, horário de pico, lista de compras para 7 dias. |
| **Backup** | Backup diário automático (guarda os 10 últimos), backup manual, envio do arquivo, cópia para uma pasta escolhida (cartão, pendrive) e restauração. |
| **Facilidade** | Tutorial guiado com áudio, letras ajustáveis, confirmação em toda operação de dinheiro, cancelamento de vendas e lançamentos, histórico de alterações. |

## Privacidade e funcionamento offline

- O app **não pede permissão de internet**. Não existe servidor: banco de
  dados SQLite, fotos e configurações ficam no armazenamento do app.
- O backup do Google Drive automático está desligado de propósito; o backup é
  feito pelo próprio app, num arquivo que o comerciante controla.
- Voz: usa o reconhecimento do próprio Android pedindo o modo no aparelho.
  No Android 12+ com reconhecimento local disponível, nada sai do celular. Em
  aparelhos mais antigos, o comerciante deve baixar o pacote offline de
  "Português (Brasil)" (Configurações do Google > Voz > Reconhecimento
  offline); sem ele, o Android pode usar a internet para transcrever a fala.
- A "inteligência" (entender a frase, achar o produto mesmo com erro,
  analisar o negócio) é feita por regras no próprio app, sem enviar dados.
- O código de barras é lido pelo ML Kit com modelo embutido no APK.

## Estrutura

```
apps/mercadinho/
├── core/   Kotlin puro, testado em qualquer JVM (sem Android)
│   ├── voz/Interpretador.kt     frase falada -> comando
│   ├── pix/Pix.kt               BR Code (Copia e Cola), CRC16, validação de chaves
│   ├── impressao/               comprovante em colunas e comandos ESC/POS
│   ├── relatorio/Relatorio.kt   lucro, CMV, ranking, períodos
│   └── ia/Conselheiro.kt        dicas e lista de compras
└── app/    Android (Jetpack Compose + Room + CameraX + ML Kit)
    ├── dados/       banco local, repositório com transações, preferências
    ├── voz/         reconhecimento de fala, voz, assistente
    ├── servicos/    impressora Bluetooth, WhatsApp, backup, catálogo, imagens
    └── ui/          telas
```

O app compila as fontes de `core/` diretamente; os testes do núcleo rodam sem
Android:

```bash
cd apps/mercadinho
./gradlew -p core test        # 24 testes: voz, Pix, comprovante, relatórios, dicas
./gradlew assembleDebug       # precisa do Android SDK (ANDROID_HOME)
```

## Como conseguir o APK

Todo push nesta pasta roda o workflow **Mercadinho Fácil - APK** no GitHub
Actions. Na execução, em *Artifacts*, baixe `mercadinho-apk`: tem o APK de
teste (debug), o APK de release e o `.aab` para a Play Store.

Para vender, veja [`docs/MERCADINHO-VENDER.md`](../../docs/MERCADINHO-VENDER.md).
