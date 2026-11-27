/** A block of the outline: 0-based line range, `to` inclusive. */
export interface OutlineBlock {
  id: string;
  label: string;
  from: number;
  to: number;
  /** A step block (it can fold). */
  step?: boolean;
  /** Indented in the outline. */
  nested?: boolean;
}

/** The outline of one workflow in the document: document head, workflow, inputs, each step, outputs. */
export function outlineOf(lines: readonly string[], workflowId: string, stepIds: readonly string[]): OutlineBlock[] {
  const at = (re: RegExp, from = 0) => {
    for (let i = from; i < lines.length; i++) if (re.test(lines[i] ?? '')) return i;
    return -1;
  };
  const wfs = at(/^workflows:/);
  const wf = at(new RegExp(`^\\s*- workflowId: ${workflowId}\\s*$`));
  if (wfs < 0 || wf < 0) return [];
  const inputs = at(/^ {4}inputs:/, wf);
  const steps = at(/^ {4}steps:/, wf);
  const outputs = at(/^ {4}outputs:/, steps < 0 ? wf : steps);
  // The workflow ends at the next workflow, a comment at its level, or the end of the file.
  let end = at(/^ {2}(- workflowId:|#)/, wf + 1);
  if (end < 0) end = lines.length;
  while (end > 0 && (lines[end - 1] ?? '').trim() === '') end--;
  const blocks: OutlineBlock[] = [{ id: 'doc', label: 'arazzo · info · sources', from: 0, to: wf - 1 }];
  blocks.push({ id: 'workflow', label: workflowId, from: wf, to: (inputs > 0 ? inputs : steps) - 1 });
  if (inputs > 0) blocks.push({ id: 'inputs', label: 'inputs', from: inputs, to: steps - 1, nested: true });
  const starts = stepIds.map((id) => at(new RegExp(`^\\s*- stepId: ${id}\\s*$`), steps)).filter((i) => i >= 0);
  stepIds.forEach((id, k) => {
    const from = starts[k];
    if (from === undefined) return;
    const next = starts[k + 1] ?? (outputs > 0 ? outputs : end);
    blocks.push({ id, label: `${k + 1} · ${id}`, from, to: next - 1, step: true, nested: true });
  });
  if (outputs > 0) blocks.push({ id: 'outputs', label: 'outputs', from: outputs, to: end - 1, nested: true });
  return blocks;
}

export type YamlSpan = { text: string; cls?: string };

/** One YAML line split in coloured spans: keys, `:` and `- ` marks, runtime expressions, numbers and arrays, comments. */
export function yamlSpans(text: string): YamlSpan[] {
  const hash = text.search(/(^\s*#|\s+#\s)/);
  const body = hash >= 0 ? text.slice(0, hash) : text;
  const comment = hash >= 0 ? text.slice(hash) : '';
  const value = (v: string): YamlSpan[] =>
    !v ? [] : [{ text: v, cls: v.startsWith('$') || v.startsWith('"{$') ? 'y-expr' : /^[[>0-9^]/.test(v) ? 'y-lit' : undefined }];
  const out: YamlSpan[] = [];
  const kv = /^(\s*)(- )?([A-Za-z_][\w-]*)(:)(\s?)(.*)$/.exec(body);
  if (kv) {
    out.push({ text: kv[1] ?? '' });
    if (kv[2]) out.push({ text: '- ', cls: 'y-mark' });
    out.push({ text: kv[3] ?? '', cls: 'y-key' }, { text: ':', cls: 'y-mark' }, { text: kv[5] ?? '' }, ...value(kv[6] ?? ''));
  } else {
    const item = /^(\s*)(- )?(.*)$/.exec(body);
    if (item) {
      out.push({ text: item[1] ?? '' });
      if (item[2]) out.push({ text: '- ', cls: 'y-mark' });
      out.push(...value(item[3] ?? ''));
    }
  }
  if (comment) out.push({ text: comment, cls: 'y-comment' });
  if (!text) out.push({ text: ' ' });
  return out;
}
