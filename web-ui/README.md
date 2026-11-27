# Microcks v2 Web UI

New web UI for Microcks 2.0 (M2-009), built with **SvelteKit 3**, **TailwindCSS 4** and shadcn-style components,
following the [Deep Ink](https://github.com/sebastiendegodez/microcks-deep-ink) design system.

All screens of the Deep Ink demo are now ported (iterations 1 to 4). Iteration 1 covers the app shell (header, side menu, footer, command bar), the **Services** page and the
**Sources** pages (list, detail, *Add a source*) and the **Run a test** page (`/tests/create?service=<id>`), the **Administration** page (`/admin/<users|groups|secrets|snapshots|configuration>`), in light and dark themes.

Run a test limits: *Launch* records a request via `POST /api/tests` and opens `/tests/runner/<id>` with a **simulated** run (no test engine); the CI snippet mirrors the form live.

Administration status:
- **Groups and Users are wired to the backend** (`/api/groups`, M2-003). Groups and memberships are stored by Microcks; the Users tab lists the accounts Microcks creates at first OIDC sign-in (`/api/users`, M2-032) joined with memberships; admins can disable, enable or remove them. A Keycloak demo is in `v2/demo/keycloak/`.
- **Secrets are real**: stored by the backend (`/api/secrets`), Password or Token with an optional CA certificate; values are never returned to the browser. "Used by" counts the sources that reference the secret. OAuth2 secrets are not supported by the backend.
- **Snapshots are real**: they use the backend repository export/import (`/api/export`, `/api/import`): services, resources, examples, no sources.
- **Configuration** is read-only and reported by the backend (`/api/features/config`, `/api/auth/config`).

## Authentication (M2-002)

The UI server asks the backend (`MICROCKS_API_URL`, default `http://localhost:8080`) whether authentication is enabled
(`GET /api/auth/config`; an unreachable backend counts as disabled). When it is:

- `src/hooks.server.ts` answers `401` to `/api/**` and redirects documents to `/login` when there is no valid session.
- `/login` starts `GET /api/auth/login`, which redirects to the backend OIDC login with
  `redirect_uri=<ui-origin>/api/auth/callback`. That URI must be listed in the backend
  `microcks.auth.oidc.allowed-redirect-uris`. Behind a proxy, set `ORIGIN` (adapter-node) to the public URL.
- The callback receives the Microcks JWT (`?token=`) and stores it in the HttpOnly `microcks_session` cookie
  (`SameSite=Lax`, `Secure` over HTTPS, lifetime of the token). The token never reaches client-side JavaScript.
- `GET /api/auth/session` returns the user for the shell avatar; `POST /api/auth/logout` clears the cookie.
- `locals.token` carries the Bearer to add when BFF routes are connected to the backend (they are not yet).

## Screens (iteration 4)

| Route | Page | Data |
|---|---|---|
| `/` | Cockpit | real counts from the store; tests, conformance, traffic = placeholders |
| `/services/:id/:tab?`, `/services/:id/operations/:op`, `/services/:id/copilot` | Service, operation behavior, AI Copilot | real services, operations and contract examples, "Used in N workflows" from the imported Arazzo documents; Copilot generation = placeholder |
| `/workflows`, `/workflows/:doc/:wf/:tab?`, `.../run`, `/workflows/runs/:id` | Workflows, Loom, run setup, run | real Arazzo documents, workflows, steps, data threads, source and links from `/api/arazzo` (placeholder only when the backend is unreachable); runs are launched on Microcks and executed by the Arazzo minion (mocks or implementations), read back with their exchanges and history; sessions and examples are simulated (later stories) |
| `/tests` (redirect), `/tests/service/:id`, `/tests/runner/:id`, `/tests/agent/:id?` | Test history, test run, Agent test | demo history + simulated runs |
| `/metrics/:id?` | Usage & conformance | generated placeholder series |
| `/traces` | Live traces (SSE) | placeholders |
| `/hub/:pkg?` | Hub catalogue and install | read directly by the browser from the Hub (`/mocks`, endpoint from the `microcks-hub` feature); each API version installs through `/api/hub/install` (importers or one-shot download) |
| `/map`, any other | Screen map, 404 | routes really present |

Placeholders live in `app/src/lib/placeholders/*.ts` (header `PLACEHOLDER:`) and `lib/server/*`; the UI shows a discreet "Demo data" marker. BFF routes under `app/src/routes/api/**` mirror Microcks endpoints where they exist (`/api/services/{id}`, `/api/tests/service/{id}`, `/api/tests/{id}`, `/api/metrics/invocations|conformance/...`); others (traces, workflows, agent runs) have no Microcks counterpart yet. The BFF is **not yet connected to the Quarkus backend**.

Per-area screenshot scripts: `app/scripts/screenshots-{catalog,workflows,runs,testing,observe}.mjs`.

## Layout

```
web-ui/
├── packages/deep-ink/   @microcks/deep-ink: Svelte components, Deep Ink tokens/styles, Tailwind bridge
├── app/                 SvelteKit app (adapter-node)
│   ├── src/lib/         client helpers (importing, sources, format, catalog, nav, types)
│   ├── src/lib/server/  in-memory store and artifact parser (demo data layer)
│   ├── src/routes/      (app)/services, (app)/sources/[[id]], (app)/sources/new, /api/*
│   ├── src/styles/      page-level CSS copied from the Deep Ink demo
│   └── scripts/screenshots.mjs
```

Proof screenshots (dark and light) are in `v2/screenshots/`.

## Tokens and packaging

- Design tokens and component styles come from Deep Ink, synced from commit
  `245fe0b48611ecc3a0fc1737a15fee0480893262`. They live in `packages/deep-ink` and are exposed to Tailwind through a
  bridge, so utilities such as `bg-surface` resolve to the shared CSS variables.
- Themes are switched with the `data-theme` attribute; every token has a light and a dark value.
- Apps import `@microcks/deep-ink` (components) and its CSS entry points. Do not hard-code colors in the app.
- To resync: copy the upstream styles into `packages/deep-ink`, and the demo page CSS into `app/src/styles`.

## Running

```
cd web-ui && npm install
cd app && npm run dev        # http://127.0.0.1:5173
npm run check                # svelte-check
npm run build                # adapter-node output in app/build
node scripts/screenshots.mjs ../../screenshots   # needs the dev server and Playwright Chromium
```

## Data layer

The UI is not wired to the Quarkus backend yet. `src/lib/server/store.ts` is an in-memory store, reset on restart:

- `seed()` imports the contracts of `v2/samples` (18 files).
- `parser.ts` recognizes OpenAPI, AsyncAPI, gRPC/Protobuf, GraphQL, WSDL, Postman, SoapUI, HAR, APIMetadata and
  OpenCollection. Arazzo and A2A are not claimed.
- Imports are keyed by service name and version; secondary artifacts merge into the existing service.
- URL imports do a real `fetch` (8 s timeout, 5 MB limit); `/samples/<file>` URLs resolve locally.

## Known limits

- No test or conformance data: services show "Never tested", with `—` for conformance and mock calls.
- No mock engine: the done step shows `http://localhost:8080` plus the mock path as a default Microcks address.
- *Edit source* is not implemented; Git secrets are not available (Secret select is disabled).
- One file per drop. "Run a test" is not offered after import.
- Seed configuration is for demo only: origins (upload, URL, Git) are assigned arbitrarily, one source is inactive,
  and last-import times come from the file modification times.
