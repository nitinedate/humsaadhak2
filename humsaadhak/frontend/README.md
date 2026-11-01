# humsaadhak-web

React 19 + TypeScript + Vite. Built into the Spring Boot JAR by Maven; runs on its own for development.

| Command | What it does |
|---|---|
| `npm run dev` | Dev server on :5173, proxying `/api` to Spring Boot on :8080 |
| `npm run demo` | Same UI on built-in sample data, no backend needed (sign in with the role buttons) |
| `npm test` | Unit tests + end-to-end tests that click through every screen for every role (demo data, jsdom) |
| `npm run typecheck` | Strict TypeScript check |
| `npm run build` | Production bundle in `dist/` (demo data is never included) |
| `npm run build:preview` | One self-contained HTML file of the demo, for sharing |

## Structure
- `src/app` — shell (fixed left panel), routing, auth context, theme provider
- `src/features/<screen>` — one folder per screen: auth, dashboards, approvals, matching, giving, catalogue, documents,
  organisation, admin, settings
- `src/shared` — API client (CSRF, RFC 9457 errors), types mirroring the Java records, reference-data hooks, formatters,
  UI components (charts, drawer, history panel, status pill)
- `src/styles/themes.css` — the four themes as design tokens; `global.css` uses only those tokens

## Themes
Lavender (default), HumSaadhak sand, Ocean mint and Night. All use light, low-glare
combinations (light side panel, light highlight cards, soft chart colours); Night is a soft dark slate with pastel accents.
`src/styles/themes.test.ts` checks every theme for WCAG AA text contrast and that charts stay soft but visible. To add one: add a `[data-theme='NAME']` block to `themes.css`, an entry
to `THEMES` in `src/app/theme.tsx`, a chart palette in `src/shared/ui/colors.ts`, and the name to the Java `Theme` enum.

## Security notes
The session cookie is HttpOnly; the client echoes the `XSRF-TOKEN` cookie as `X-XSRF-TOKEN`. Menu items are hidden by
permission, but the server enforces every rule. File downloads use signed links that expire after five minutes.
