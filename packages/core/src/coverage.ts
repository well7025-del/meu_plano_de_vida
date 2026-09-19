/**
 * Varredura de cobertura.
 *
 * Responde a pergunta que decide o negocio: a partir de quantos motoristas com
 * o app o mapa passa a ser util? Roda o mesmo bairro varias vezes, mudando so
 * a penetracao, e mede tres coisas:
 *
 *   pontos    quantos pontos o mapa mostra em media;
 *   precisao  quantos desses pontos tinham vaga real a menos de 40 m;
 *   utilidade em que fracao das vezes que alguem foi procurar vaga o app
 *             tinha uma sugestao verdadeira perto do destino.
 *
 * A ultima e a unica que o usuario sente.
 *
 *   npm run cobertura
 */
import { runCity, type CityResult } from './city.js';

const LEVELS = [0.02, 0.05, 0.1, 0.2, 0.3, 0.5, 0.8];
const SEEDS = [7, 23, 91];

function mean(values: number[]): number {
  return values.reduce((a, b) => a + b, 0) / values.length;
}

function run(penetration: number): CityResult & { utility: number } {
  const runs = SEEDS.map((seed) => runCity({ penetration, seed, durationS: 4 * 3600 }));
  const totalSearches = runs.reduce((a, r) => a + r.searches, 0);
  const totalTrue = runs.reduce((a, r) => a + r.withTrueSuggestion, 0);
  const first = runs[0] as CityResult;
  return {
    ...first,
    appDrivers: Math.round(mean(runs.map((r) => r.appDrivers))),
    events: Math.round(mean(runs.map((r) => r.events))),
    avgDots: mean(runs.map((r) => r.avgDots)),
    precision: mean(runs.filter((r) => r.avgDots > 0).map((r) => r.precision)),
    searches: totalSearches,
    withSuggestion: runs.reduce((a, r) => a + r.withSuggestion, 0),
    withTrueSuggestion: totalTrue,
    utility: totalSearches > 0 ? totalTrue / totalSearches : 0,
  };
}

function main(): void {
  const first = runCity({ penetration: 0, durationS: 60, seed: 7 });
  const areaKm2 = (0.72 * 0.72).toFixed(2);

  console.log('\n=== Vagas — quanta gente o bairro precisa ===');
  console.log(`bairro de ${areaKm2} km2, ${first.totalSpots} vagas de meio-fio, 60 carros em movimento`);
  console.log('4 h simuladas, media de 3 rodadas\n');
  console.log('  app    motoristas  eventos   pontos   precisão   utilidade');
  console.log('  ────   ──────────  ───────   ──────   ────────   ─────────');

  for (const level of LEVELS) {
    const r = run(level);
    const pct = `${Math.round(level * 100)}%`.padStart(4);
    console.log(
      `  ${pct}   ${String(r.appDrivers).padStart(10)}  ${String(r.events).padStart(7)}   ` +
        `${r.avgDots.toFixed(1).padStart(6)}   ${(r.precision * 100).toFixed(0).padStart(7)}%   ` +
        `${(r.utility * 100).toFixed(0).padStart(8)}%`,
    );
  }

  console.log('\n  utilidade = das vezes em que alguem foi procurar vaga, quantas o app');
  console.log('              tinha um ponto verdadeiro a menos de 150 m do destino\n');
}

main();
