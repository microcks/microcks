import hljs from 'highlight.js/lib/core';
import json from 'highlight.js/lib/languages/json';
import xml from 'highlight.js/lib/languages/xml';

hljs.registerLanguage('json', json);
hljs.registerLanguage('xml', xml);

export interface Payload {
  /** Highlighted HTML (escaped by highlight.js), or the escaped text when the language is unknown. */
  html: string;
  lines: number;
}

const escapeHtml = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/** Pretty-prints JSON (2 spaces) and highlights JSON and XML payloads; anything else stays plain text. */
export function renderPayload(text: string): Payload {
  const trimmed = text.trim();
  let code = text;
  let language: 'json' | 'xml' | undefined;
  if (/^[[{]/.test(trimmed)) {
    try {
      code = JSON.stringify(JSON.parse(trimmed), null, 2);
      language = 'json';
    } catch {
      language = undefined;
    }
  } else if (/^<[?!A-Za-z]/.test(trimmed)) {
    language = 'xml';
  }
  return {
    html: language ? hljs.highlight(code, { language }).value : escapeHtml(code),
    lines: code.split('\n').length
  };
}
