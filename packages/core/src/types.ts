/**
 * Tipos compartilhados do motor de deteccao.
 *
 * Todo o pipeline roda no dispositivo: a unica coisa que sai do celular e um
 * `ParkingEvent` ja consolidado (ponto + horario + confianca), nunca a
 * trajetoria bruta. Ver docs/PRIVACIDADE.md.
 */

/** Amostra de localizacao vinda do SO (expo-location / CLLocation / FusedLocation). */
export interface LocationSample {
  /** Epoch em milissegundos. */
  t: number;
  lat: number;
  lon: number;
  /** Velocidade instantanea em m/s reportada pelo GPS. `null` quando indisponivel. */
  speed?: number | null;
  /** Raio de erro horizontal em metros. */
  accuracy?: number | null;
  /** Precisao da velocidade em m/s, quando o SO informa. */
  speedAccuracy?: number | null;
  /**
   * Modo de transporte sugerido pela API nativa de reconhecimento de atividade
   * (Android ActivityRecognition / iOS CMMotionActivity). Usado como reforco,
   * nunca como unica fonte.
   */
  activityHint?: ActivityHint | null;
}

export type ActivityHint = 'still' | 'walking' | 'running' | 'cycling' | 'vehicle' | 'unknown';

/** Modo de movimento consolidado pelo classificador. */
export type MotionMode = 'unknown' | 'still' | 'walking' | 'vehicle';

/** Estado do usuario na maquina de estados. */
export type DetectorState =
  | 'unknown'
  | 'walking'
  | 'in_vehicle'
  | 'vehicle_stopped';

export type ParkingEventKind =
  /** O motorista saiu: uma vaga foi LIBERADA naquele ponto. */
  | 'departure'
  /** O motorista estacionou: uma vaga foi OCUPADA naquele ponto. */
  | 'arrival';

export interface ParkingEvent {
  kind: ParkingEventKind;
  /** Ponto ancora do evento: onde o carro estava/ficou estacionado. */
  lat: number;
  lon: number;
  /** Momento em que a vaga foi liberada/ocupada (nao o momento da confirmacao). */
  t: number;
  /** Momento em que o motor teve certeza suficiente para emitir. */
  confirmedAt: number;
  /** 0..1 — quanto o motor confia que isto foi mesmo um evento de rua. */
  confidence: number;
  /** Raio de incerteza do ponto ancora, em metros. */
  radiusM: number;
  /** Fatores que compuseram a confianca, para auditoria e tuning. */
  signals: EventSignals;
}

export interface EventSignals {
  /** Duracao da caminhada adjacente ao evento, em segundos. */
  walkDurationS: number;
  /** Distancia percorrida a pe se afastando/aproximando do ponto ancora, em metros. */
  walkDistanceM: number;
  /** Duracao do trecho veicular adjacente, em segundos. */
  driveDurationS: number;
  /** Distancia percorrida de carro no trecho adjacente, em metros. */
  driveDistanceM: number;
  /** Tempo parado entre dirigir e caminhar (so em `arrival`), em segundos. */
  stopDurationS: number;
  /** Melhor precisao de GPS observada perto do ponto ancora, em metros. */
  anchorAccuracyM: number;
  /** Penalidades aplicadas (zona de exclusao, suspeita de onibus etc.). */
  penalties: string[];
}

/**
 * Contexto do mapa no ponto ancora, injetado pelo app a partir de dados
 * offline (extrato do OpenStreetMap + camadas da prefeitura). O motor nao
 * carrega mapa nenhum: ele so consulta este contrato.
 */
export interface LocationContext {
  /** O ponto cai sobre uma via onde carro trafega? */
  onDrivableStreet?: boolean;
  /** Estacionar ali e permitido (nao e faixa, hidrante, garagem, ponto)? */
  parkingAllowed?: boolean;
  /** O ponto cai dentro de estacionamento privado/shopping? */
  insidePrivateLot?: boolean;
  /** Ha ponto de onibus/estacao a menos de ~30 m? */
  nearTransitStop?: boolean;
  /** Classe da via: eventos em via expressa quase sempre sao ruido. */
  roadClass?: 'residential' | 'tertiary' | 'secondary' | 'primary' | 'motorway' | 'service' | 'unknown';
}

/** Regiao onde eventos nunca devem ser publicados (garagem de casa, trabalho...). */
export interface ExclusionZone {
  id: string;
  lat: number;
  lon: number;
  radiusM: number;
  label?: string;
}

export interface DetectorConfig {
  /** Acima disso a amostra e descartada por ruido de GPS (m). */
  maxAccuracyM: number;
  /** Limite superior da faixa "parado" (m/s). */
  stillMaxSpeed: number;
  /** Faixa de caminhada (m/s). */
  walkMinSpeed: number;
  walkMaxSpeed: number;
  /** A partir daqui e considerado veiculo (m/s). ~15 km/h. */
  vehicleMinSpeed: number;
  /** Janela do filtro de mediana (numero de amostras). */
  medianWindow: number;
  /** Tempo que um modo candidato precisa se sustentar para virar o modo atual (s). */
  dwellStillS: number;
  dwellWalkS: number;
  dwellVehicleS: number;
  /** Tempo minimo em cada modo para confirmar a transicao (s). */
  minWalkDurationS: number;
  minVehicleDurationS: number;
  /** Parada minima para considerar que o veiculo estacionou (s). */
  minStopDurationS: number;
  /** Deslocamento minimo de carro para validar uma saida (m). */
  minDriveDistanceM: number;
  /** Distancia minima que o usuario precisa se afastar a pe do ponto de parada (m). */
  minWalkAwayDistanceM: number;
  /** Raio base de incerteza do ponto ancora (m). */
  baseAnchorRadiusM: number;
  /** Eventos abaixo desta confianca nao sao emitidos. */
  minConfidence: number;
  /** Zonas onde nao se publica evento. */
  exclusionZones: ExclusionZone[];
  /**
   * Consulta de contexto do mapa. Sincrona e local de proposito: roda em
   * background, sem rede. Ausente = motor opera so com o perfil de movimento.
   */
  contextProvider?: (p: { lat: number; lon: number }) => LocationContext | null;
}

export const DEFAULT_DETECTOR_CONFIG: DetectorConfig = {
  maxAccuracyM: 50,
  stillMaxSpeed: 0.7,
  walkMinSpeed: 0.7,
  walkMaxSpeed: 2.8,
  vehicleMinSpeed: 4.2,
  medianWindow: 5,
  dwellStillS: 20,
  dwellWalkS: 12,
  dwellVehicleS: 15,
  minWalkDurationS: 25,
  minVehicleDurationS: 45,
  minStopDurationS: 90,
  minDriveDistanceM: 200,
  minWalkAwayDistanceM: 40,
  baseAnchorRadiusM: 15,
  minConfidence: 0.45,
  exclusionZones: [],
};
