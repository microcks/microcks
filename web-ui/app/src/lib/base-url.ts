/** Port of the Vite dev server of the web UI: the Microcks backend is then served on DEV_BACKEND_PORT of the same host. */
const DEV_UI_PORT = '5173';
const DEV_BACKEND_PORT = '8080';

/** Public base URL of Microcks (mocks and API), derived from the browser location. */
export function microcksBaseUrl(): string {
  if (typeof window === 'undefined') return `http://localhost:${DEV_BACKEND_PORT}`;
  const { protocol, hostname, port, origin } = window.location;
  return port === DEV_UI_PORT ? `${protocol}//${hostname}:${DEV_BACKEND_PORT}` : origin;
}

/** Prefixes a server relative mock path (`/rest/...`) with the Microcks base URL; other values (grpc://...) are kept. */
export const withMicrocksBase = (url: string): string => (url.startsWith('/') ? `${microcksBaseUrl()}${url}` : url);
