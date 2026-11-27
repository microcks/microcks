import type { Criterion } from './types.js';

/**
 * Values a runtime expression can read. Every field is optional: an expression that
 * cannot be resolved yields `undefined` and the criterion that uses it does not hold.
 */
export interface EvaluationContext {
  statusCode?: number;
  body?: unknown;
  headers?: Record<string, string>;
  inputs?: Record<string, unknown>;
  steps?: Record<string, { outputs: Record<string, unknown> }>;
}

function pointer(value: unknown, path: string): unknown {
  if (path === '' || path === '/') return value;
  let current: unknown = value;
  for (const raw of path.split('/').slice(1)) {
    const key = raw.replace(/~1/g, '/').replace(/~0/g, '~');
    if (Array.isArray(current)) current = current[Number(key)];
    else if (current !== null && typeof current === 'object') current = (current as Record<string, unknown>)[key];
    else return undefined;
  }
  return current;
}

/** Parses a literal on the right of a condition: 200, 'available', "x", true, false, null. */
function parseLiteral(text: string): unknown {
  const t = text.trim();
  if (/^-?\d+(\.\d+)?$/.test(t)) return Number(t);
  if ((t.startsWith("'") && t.endsWith("'")) || (t.startsWith('"') && t.endsWith('"'))) return t.slice(1, -1);
  if (t === 'true') return true;
  if (t === 'false') return false;
  if (t === 'null') return null;
  return t;
}

/**
 * Resolves an Arazzo runtime expression: `$statusCode`, `$response.body#/json/pointer`,
 * `$response.header.<name>`, `$inputs.<name>`, `$steps.<id>.outputs.<name>`.
 * A string that is not an expression is parsed as a literal.
 */
export function resolveExpression(expression: string, ctx: EvaluationContext): unknown {
  const e = expression.trim();
  if (!e.startsWith('$')) return parseLiteral(e);
  if (e === '$statusCode') return ctx.statusCode;
  if (e.startsWith('$response.body')) {
    const hash = e.indexOf('#');
    return hash < 0 ? ctx.body : pointer(ctx.body, e.slice(hash + 1));
  }
  if (e.startsWith('$response.header.')) {
    const name = e.slice('$response.header.'.length).toLowerCase();
    const found = Object.entries(ctx.headers ?? {}).find(([k]) => k.toLowerCase() === name);
    return found?.[1];
  }
  if (e.startsWith('$inputs.')) return ctx.inputs?.[e.slice('$inputs.'.length)];
  const step = /^\$steps\.([^.]+)\.outputs\.(.+)$/.exec(e);
  if (step) return ctx.steps?.[step[1] as string]?.outputs[step[2] as string];
  return undefined;
}

export interface CriterionEvaluation {
  held: boolean;
  kind: 'simple' | 'regex';
  /** Human readable evaluation, e.g. `$statusCode = 200`. */
  evaluation: string;
}

const SIMPLE = /^(\S+)\s*(==|!=|<=|>=|<|>)\s*(.+)$/;

function show(value: unknown): string {
  return value === undefined ? 'undefined' : JSON.stringify(value);
}

/**
 * Evaluates a success criterion.
 * - simple: `<expression> <op> <literal|expression>` with `==`, `!=`, `<`, `>` (and `<=`, `>=`).
 *   A condition without an operator holds when its expression is truthy.
 * - regex: the `context` expression is converted to a string and tested against `condition`.
 */
export function evaluateCriterion(criterion: Criterion, ctx: EvaluationContext): CriterionEvaluation {
  if (criterion.type === 'regex') {
    const actual = resolveExpression(criterion.context, ctx);
    let held = false;
    try {
      held = actual !== undefined && new RegExp(criterion.condition).test(String(actual));
    } catch {
      held = false;
    }
    return { held, kind: 'regex', evaluation: `${criterion.context} = ${show(actual)}` };
  }

  const match = SIMPLE.exec(criterion.condition.trim());
  if (!match) {
    const actual = resolveExpression(criterion.condition, ctx);
    return { held: Boolean(actual), kind: 'simple', evaluation: `${criterion.condition} = ${show(actual)}` };
  }
  const [, lhs, op, rhsText] = match as unknown as [string, string, string, string];
  const actual = resolveExpression(lhs, ctx);
  const expected = resolveExpression(rhsText, ctx);
  let held: boolean;
  switch (op) {
    case '==':
      held = actual === expected;
      break;
    case '!=':
      held = actual !== expected;
      break;
    default: {
      held =
        typeof actual === 'number' &&
        typeof expected === 'number' &&
        (op === '<' ? actual < expected : op === '>' ? actual > expected : op === '<=' ? actual <= expected : actual >= expected);
    }
  }
  return { held, kind: 'simple', evaluation: `${lhs} = ${show(actual)}` };
}

/** True when every criterion holds (an empty list holds). */
export function allCriteriaHold(criteria: readonly Criterion[], ctx: EvaluationContext): boolean {
  return criteria.every((c) => evaluateCriterion(c, ctx).held);
}
