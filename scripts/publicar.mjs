/**
 * Publica o Vagas na Cloudflare e, quando falha, explica a falha.
 *
 * O wrangler erra com codigo e stack. Para quem esta publicando pela primeira
 * vez isso nao diz o que fazer — e o que decide se o teste de campo acontece
 * esta semana ou daqui a um mes. Este script traduz os erros conhecidos em uma
 * instrucao.
 */
import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const worker = resolve(raiz, 'services/worker');

const vermelho = (s) => `\x1b[31m${s}\x1b[0m`;
const verde = (s) => `\x1b[32m${s}\x1b[0m`;
const negrito = (s) => `\x1b[1m${s}\x1b[0m`;

function falhar(titulo, passos) {
  console.error(`\n${vermelho('✘')} ${negrito(titulo)}\n`);
  for (const p of passos) console.error(`  ${p}`);
  console.error(
    `\n  Se nada disso resolver, há um caminho sem terminal nenhum:\n` +
      `  ${negrito('docs/PUBLICAR.md')} — você cadastra dois segredos no GitHub e clica num botão.\n`,
  );
  process.exit(1);
}

// --- 1. Node ---------------------------------------------------------------
const [maior, menor] = process.versions.node.split('.').map(Number);
if (maior < 20) {
  falhar(`Node ${process.versions.node} é antigo demais`, [
    'O wrangler precisa do Node 20 ou mais novo; este projeto pede 22.5+.',
    'Baixe a versão LTS em https://nodejs.org, feche o terminal, abra de novo',
    'e rode novamente.',
  ]);
}
if (maior === 22 && menor < 5) {
  console.warn(
    `\n⚠ Node ${process.versions.node}: o servidor local (npm run testar) precisa do 22.5+.\n` +
      '  Publicar na Cloudflare deve funcionar mesmo assim.\n',
  );
}

// --- 2. Build --------------------------------------------------------------
function rodar(comando, args, cwd, titulo) {
  console.log(`\n${negrito('==>')} ${titulo}`);
  const r = spawnSync(comando, args, { cwd, stdio: 'inherit', shell: process.platform === 'win32' });
  if (r.status !== 0) {
    falhar(`Falhou: ${titulo}`, [
      'O erro completo está logo acima.',
      'Se for "command not found" ou "não é reconhecido", o Node/npm não está instalado',
      'ou não está no PATH: reinstale pelo https://nodejs.org.',
    ]);
  }
}

rodar('npm', ['run', 'build', '-w', '@vagas/core'], raiz, 'compilando o motor');
rodar('npm', ['run', 'assets'], worker, 'montando os arquivos do app');

// --- 3. Deploy -------------------------------------------------------------
console.log(`\n${negrito('==>')} publicando na Cloudflare`);
const deploy = spawnSync('npx', ['wrangler', 'deploy'], {
  cwd: worker,
  encoding: 'utf8',
  shell: process.platform === 'win32',
});

const saida = `${deploy.stdout ?? ''}${deploy.stderr ?? ''}`;
process.stdout.write(saida);

if (deploy.status === 0) {
  const url = saida.match(/https:\/\/[\w.-]+\.workers\.dev/)?.[0];
  console.log(`\n${verde('✔')} ${negrito('Publicado.')}`);
  if (url) {
    console.log(`\n  ${negrito(url)}\n`);
    console.log('  É esse o endereço para mandar no grupo. Cada pessoa abre no celular');
    console.log('  e adiciona à tela inicial. O passo a passo do teste está em');
    console.log('  docs/TESTE-DE-CAMPO.md.\n');
  }
  process.exit(0);
}

// --- 4. Traduzir o erro ----------------------------------------------------
const erros = [
  {
    quando: /CLOUDFLARE_API_TOKEN|non-interactive|not authenticated|wrangler login/i,
    titulo: 'A Cloudflare não sabe quem é você',
    passos: [
      'Rode primeiro:  npx wrangler login',
      'Ele abre o navegador para você autorizar.',
      '',
      'Se o navegador não abrir (comum em WSL ou máquina remota), crie um token em',
      'https://dash.cloudflare.com/profile/api-tokens (modelo "Edit Cloudflare Workers") e:',
      '  Linux/Mac:  export CLOUDFLARE_API_TOKEN=seu-token',
      '  Windows:    set CLOUDFLARE_API_TOKEN=seu-token',
    ],
  },
  {
    quando: /10015|not entitled|workers\.dev subdomain|10007/i,
    titulo: 'A conta ainda não tem os Workers ativados',
    passos: [
      'Numa conta nova é preciso visitar a área uma vez e escolher o subdomínio:',
      '',
      '  1. Abra https://dash.cloudflare.com',
      '  2. Menu da esquerda → Workers & Pages',
      '  3. Escolha um subdomínio (ex.: seunome) e confirme',
      '  4. Se pedir plano, escolha o Free',
      '',
      'Depois rode este comando de novo.',
    ],
  },
  {
    quando: /10000|Authentication error|invalid.*token|Unable to authenticate/i,
    titulo: 'O token foi recusado',
    passos: [
      'Costuma ser token copiado pela metade, expirado, ou de outra conta.',
      'Crie um novo em https://dash.cloudflare.com/profile/api-tokens',
      'com o modelo "Edit Cloudflare Workers", e tente de novo.',
    ],
  },
  {
    quando: /not_authorized|9109|Authorization|permission/i,
    titulo: 'O token não tem permissão para criar um Worker',
    passos: [
      'Editar um Worker existente e criar o primeiro são permissões diferentes.',
      'Em https://dash.cloudflare.com/profile/api-tokens crie um token com',
      '"Edit Cloudflare Workers" e confirme que ele inclui',
      'Account → Workers Scripts → Edit.',
    ],
  },
  {
    quando: /D1_ERROR|no such table|database.*not found|7404/i,
    titulo: 'O banco D1 não está como o esperado',
    passos: [
      'Recrie o esquema:',
      '  npx wrangler d1 execute vagas --remote --file=services/worker/schema.sql',
      '',
      'Se o banco nem existe, crie e anote o id em services/worker/wrangler.toml:',
      '  npx wrangler d1 create vagas',
    ],
  },
  {
    quando: /fetch failed|ENOTFOUND|ECONNREFUSED|ETIMEDOUT|proxy/i,
    titulo: 'Não consegui falar com a Cloudflare',
    passos: [
      'Parece problema de rede: internet caiu, firewall da empresa, ou proxy.',
      'Tente de outra rede (o 4G do celular serve) e rode de novo.',
    ],
  },
];

const achado = erros.find((e) => e.quando.test(saida));
if (achado) {
  falhar(achado.titulo, achado.passos);
}

falhar('A publicação falhou', [
  'A mensagem da Cloudflare está logo acima.',
  'Mande essa mensagem para eu olhar — as últimas 15 linhas bastam.',
]);
