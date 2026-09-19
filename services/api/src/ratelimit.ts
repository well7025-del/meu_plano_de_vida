/**
 * Limite de taxa por origem, em janela deslizante simples.
 *
 * O app nao tem contas, entao nao ha usuario a quem limitar: sobra o IP, que
 * e fraco mas suficiente para o que importa aqui — impedir que um cliente
 * modificado despeje milhares de saidas falsas e apague ou invente uma regiao
 * inteira. Em producao isto vive no gateway, nao no processo.
 */
export class RateLimiter {
  private readonly hits = new Map<string, number[]>();

  constructor(
    private readonly limit: number,
    private readonly windowMs: number,
    private readonly now: () => number = () => Date.now(),
  ) {}

  /** `true` se a requisicao pode passar. */
  allow(key: string): boolean {
    const t = this.now();
    const cutoff = t - this.windowMs;
    const list = (this.hits.get(key) ?? []).filter((x) => x > cutoff);
    if (list.length >= this.limit) {
      this.hits.set(key, list);
      return false;
    }
    list.push(t);
    this.hits.set(key, list);

    // Poda preguicosa para o mapa nao crescer sem limite.
    if (this.hits.size > 5000) {
      for (const [k, v] of this.hits) {
        if (v.every((x) => x <= cutoff)) this.hits.delete(k);
      }
    }
    return true;
  }
}
