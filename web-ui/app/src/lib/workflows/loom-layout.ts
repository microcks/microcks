import type { LoomCheckState as CheckState, LoomCheckRow as LoomCheck, LoomDrop, LoomSource, LoomStep, LoomThread } from './loom-model.js';

export interface LoomPlacement {
  stepId: string;
  /** 1-based position in the steps array (the "n" shown on the node). */
  number: number;
  /** Longest-path depth: 0 for roots. */
  column: number;
  /** Index of the step's source in `sources`. */
  lane: number;
  /** Order among the steps of the same lane and column. */
  row: number;
  /** Resolved dependencies (unknown ids removed). */
  dependsOn: string[];
}

export interface LoomEdge {
  from: string;
  to: string;
}

export interface LoomLayout {
  placements: LoomPlacement[];
  /** Number of columns (0 when there is no step). */
  columns: number;
  /** One entry per lane: how many sub-rows the lane needs (at least 1). */
  laneRows: number[];
  edges: LoomEdge[];
  warnings: string[];
}

/**
 * Pure layout of an Arazzo workflow.
 * - No step declares `dependsOn`: steps are sequential (each depends on the previous one).
 * - Some step declares it: steps without it are roots and run in parallel.
 * - column = longest-path depth, lane = index of the source, row = order among steps of the same lane and column.
 * Unknown dependencies, self-dependencies and cycles are ignored and reported in `warnings`.
 */
export function layoutLoom(steps: LoomStep[], sources: LoomSource[] = []): LoomLayout {
  const warnings: string[] = [];
  const ids = new Set(steps.map((s) => s.stepId));
  const anyDeclared = steps.some((s) => s.dependsOn !== undefined);

  const deps = new Map<string, string[]>();
  steps.forEach((s, i) => {
    if (!anyDeclared) {
      const prev = steps[i - 1];
      deps.set(s.stepId, prev ? [prev.stepId] : []);
      return;
    }
    const resolved: string[] = [];
    for (const d of s.dependsOn ?? []) {
      if (d === s.stepId) warnings.push(`Step "${s.stepId}" depends on itself; ignored.`);
      else if (!ids.has(d)) warnings.push(`Step "${s.stepId}" depends on unknown step "${d}"; ignored.`);
      else if (!resolved.includes(d)) resolved.push(d);
    }
    deps.set(s.stepId, resolved);
  });

  // Longest-path depth, with cycle protection.
  const column = new Map<string, number>();
  const visiting = new Set<string>();
  const depth = (id: string): number => {
    const known = column.get(id);
    if (known !== undefined) return known;
    if (visiting.has(id)) return 0;
    visiting.add(id);
    let d = 0;
    for (const dep of [...(deps.get(id) ?? [])]) {
      if (visiting.has(dep)) {
        warnings.push(`Dependency cycle between "${id}" and "${dep}"; "${dep}" ignored for "${id}".`);
        deps.set(id, (deps.get(id) ?? []).filter((x) => x !== dep));
        continue;
      }
      d = Math.max(d, depth(dep) + 1);
    }
    visiting.delete(id);
    column.set(id, d);
    return d;
  };
  steps.forEach((s) => depth(s.stepId));

  const laneNames = sources.map((s) => s.name);
  const laneOf = (source: string) => {
    let i = laneNames.indexOf(source);
    if (i < 0) {
      warnings.push(`Source "${source}" is not declared; a lane was added for it.`);
      laneNames.push(source);
      i = laneNames.length - 1;
    }
    return i;
  };

  const rowCount = new Map<string, number>();
  const placements: LoomPlacement[] = steps.map((s, i) => {
    const lane = laneOf(s.source);
    const col = column.get(s.stepId) ?? 0;
    const key = `${lane}:${col}`;
    const row = rowCount.get(key) ?? 0;
    rowCount.set(key, row + 1);
    return { stepId: s.stepId, number: i + 1, column: col, lane, row, dependsOn: deps.get(s.stepId) ?? [] };
  });

  const laneRows = laneNames.map(() => 1);
  for (const p of placements) laneRows[p.lane] = Math.max(laneRows[p.lane] ?? 1, p.row + 1);

  const edges: LoomEdge[] = placements.flatMap((p) => p.dependsOn.map((from) => ({ from, to: p.stepId })));

  return {
    placements,
    columns: placements.reduce((m, p) => Math.max(m, p.column + 1), 0),
    laneRows,
    edges,
    warnings,
  };
}

/** Width, in grid units, of the inputs and outputs columns (a step column is 1 unit). */
export const IO_UNITS = 0.9;

export interface LoomGrid {
  /** Step columns (at least 1). */
  steps: number;
  /** Total width in units: inputs + steps + outputs. */
  total: number;
  /** CSS grid-template-columns: inputs | one column per step depth | outputs. */
  template: string;
  /** Centre, in units, of grid column `c`: 0 = inputs, 1…steps = step depths, steps + 1 = outputs. */
  centre(c: number): number;
  /** A distance in units as a percentage of the full width ("41.38%"). */
  pct(units: number): string;
}

/**
 * Fluid horizontal geometry of the loom: the grid fills its box, so every mark is placed in percent.
 * With 4 step columns the centres are 7.76, 24.14, 41.38, 58.62, 75.86 and 92.24 %.
 */
export function loomGrid(stepColumns: number): LoomGrid {
  const steps = Math.max(1, stepColumns);
  const total = steps + 2 * IO_UNITS;
  return {
    steps,
    total,
    template: `minmax(0, ${IO_UNITS}fr) repeat(${steps}, minmax(0, 1fr)) minmax(0, ${IO_UNITS}fr)`,
    centre: (c) => (c <= 0 ? IO_UNITS / 2 : c > steps ? IO_UNITS + steps + IO_UNITS / 2 : IO_UNITS + c - 0.5),
    pct: (units) => `${((units / total) * 100).toFixed(2)}%`,
  };
}

/** Method and path of a step: explicit fields, else "GET /pastries" split at the first space. */
export function operationParts(s: Pick<LoomStep, 'operation' | 'method' | 'path'>): { method: string; path: string } {
  const space = s.operation.indexOf(' ');
  const head = space > 0 ? s.operation.slice(0, space) : '';
  const rest = space > 0 ? s.operation.slice(space + 1) : s.operation;
  const looksLikeMethod = /^[A-Z]+$/.test(head);
  return { method: s.method ?? (looksLikeMethod ? head : ''), path: s.path ?? (looksLikeMethod ? rest : s.operation) };
}

/** A thread ready to draw: id resolved, consumers grouped into drops. */
export interface WovenThread {
  id: string;
  from: string;
  expression: string;
  value?: string;
  missing?: boolean;
  drops: LoomDrop[];
}

/**
 * Normalizes threads: keeps `drops` when given, otherwise weaves legacy one-consumer threads that share a
 * producer and an expression into one row (the chip label is the consumer, or "outputs").
 */
export function weaveThreads(threads: (Omit<LoomThread, 'drops'> & { to?: string; drops?: LoomDrop[] })[]): WovenThread[] {
  const woven: WovenThread[] = [];
  for (const t of threads) {
    const id = t.id ?? t.expression;
    const drops = t.drops ?? (t.to ? [{ to: t.to, label: t.to === '$outputs' ? 'outputs' : t.to }] : []);
    const same = t.drops ? undefined : woven.find((w) => w.id === id && w.from === t.from);
    if (same) {
      for (const d of drops) if (!same.drops.some((x) => x.to === d.to && x.label === d.label)) same.drops.push(d);
      continue;
    }
    woven.push({ id, from: t.from, expression: t.expression, value: t.value, missing: t.missing, drops: [...drops] });
  }
  return woven;
}

/** One check row of a node, normalized: `state` drives the icon and colour, `word` is the spoken state. */
export interface CheckRow {
  label: string;
  state: CheckState;
  word: string;
}

const ROW_STATE: Record<LoomCheck['state'], { state: CheckState; word: string }> = {
  pass: { state: 'passed', word: 'passed' },
  fail: { state: 'failed', word: 'failed' },
  skip: { state: 'off', word: 'skipped' },
  idle: { state: 'not-evaluated', word: 'not evaluated' },
};
const WORD: Record<CheckState, string> = { passed: 'passed', failed: 'failed', off: 'off', 'not-evaluated': 'not evaluated' };

/**
 * Check rows of a step: a generic `{ label, state }[]` list is drawn as given; the legacy
 * `{ successCriteria, schemaValidation }` pair is drawn only when the run validates schemas.
 */
export function checkRows(checks: LoomStep['checks'], validatesSchemas: boolean): CheckRow[] {
  if (Array.isArray(checks)) return checks.map((c) => ({ label: c.label, ...(ROW_STATE[c.state] ?? ROW_STATE.idle) }));
  if (!validatesSchemas) return [];
  const sc = checks?.successCriteria ?? 'not-evaluated';
  const sv = checks?.schemaValidation ?? 'not-evaluated';
  return [
    { label: 'Success criteria', state: sc, word: WORD[sc] },
    { label: 'Schema validation', state: sv, word: WORD[sv] },
  ];
}
