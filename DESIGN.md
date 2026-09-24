---
name: Microcks
description: A pragmatic, dense contract workbench for mocking, testing, and observing APIs.
colors:
  workbench-blue: "#0088ce"
  workbench-blue-deep: "#00659c"
  canvas: "#ffffff"
  canvas-dark: "#263140"
  surface-muted: "#f1f1f1"
  surface-subtle: "#f5f5f5"
  surface-hover: "#edf8ff"
  text-default: "#4d5258"
  text-strong: "#363636"
  text-muted: "#9c9c9c"
  border-muted: "#cccccc"
  divider: "#eaeaea"
  on-accent: "#ffffff"
  rest-green: "#89bf04"
  soap-blue: "#39a5dc"
  event-orange: "#ec7a08"
  grpc-teal: "#379c9c"
  graphql-magenta: "#e10098"
  generic-purple: "#9c27b0"
  success-green: "#7bb33d"
  danger-red: "#dd1f26"
typography:
  display:
    fontFamily: '"Open Sans", Helvetica, Arial, sans-serif'
    fontSize: "2.4em"
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: "normal"
  headline:
    fontFamily: '"Open Sans", Helvetica, Arial, sans-serif'
    fontSize: "38px"
    fontWeight: 300
    letterSpacing: "normal"
  title:
    fontFamily: '"Open Sans", Helvetica, Arial, sans-serif'
    fontSize: "24px"
    fontWeight: 400
    letterSpacing: "normal"
  body:
    fontFamily: '"Open Sans", Helvetica, Arial, sans-serif'
    fontSize: "16px"
    fontWeight: 400
    letterSpacing: "normal"
  label:
    fontFamily: '"Open Sans", Helvetica, Arial, sans-serif'
    fontSize: "12px"
    fontWeight: 600
    letterSpacing: "normal"
  control:
    fontFamily: '"Open Sans", Helvetica, Arial, sans-serif'
    fontSize: "12px"
    fontWeight: 400
    lineHeight: 1.66666667
    letterSpacing: "normal"
  code:
    fontFamily: 'SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace'
    fontSize: "14px"
    fontWeight: 400
    letterSpacing: "normal"
rounded:
  crisp: "1px"
  control: "4px"
  panel: "6px"
  node: "8px"
  chip: "12px"
  circle: "50%"
spacing:
  xs: "4px"
  sm: "8px"
  field: "10px"
  section: "20px"
  spacious: "40px"
components:
  button-primary:
    backgroundColor: "{colors.workbench-blue}"
    textColor: "{colors.on-accent}"
    typography: "{typography.label}"
    rounded: "{rounded.crisp}"
  button-default:
    backgroundColor: "{colors.surface-muted}"
    textColor: "{colors.text-default}"
    typography: "{typography.label}"
    rounded: "{rounded.crisp}"
  input:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.text-default}"
    typography: "{typography.control}"
    rounded: "{rounded.crisp}"
    height: "26px"
  metadata-chip:
    backgroundColor: "{colors.surface-subtle}"
    textColor: "{colors.text-default}"
    rounded: "{rounded.chip}"
    padding: "0 8px"
    height: "20px"
  card:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.text-default}"
    rounded: "{rounded.crisp}"
---

# Design System: Microcks

## Overview

**Creative North Star: "The Contract Workbench"**

Microcks is a pragmatic, dense, and trustworthy operational workspace. Its interface should feel like a dependable
bench for importing contracts, inspecting services, running tests, and reading system state: familiar controls, compact
information, explicit labels, and restrained decoration. The visual system inherits PatternFly 3 and Bootstrap
conventions, then uses Microcks tokens and scoped Angular component styles to keep the experience coherent.

The system favors scanability over spectacle. Workbench Blue carries navigation, actions, and focus; the Protocol
Spectrum distinguishes API technologies; semantic colors communicate outcomes. Light and dark themes preserve the same
hierarchy and meaning. Avoid marketing-page composition, monochrome protocol treatment, deep shadows, oversized radii,
and nested soft cards in operational screens.

**Key Characteristics:**

- Compact, information-dense layouts built for repeated technical work.
- Conventional, explicit controls with restrained, predictable states.
- Blue action hierarchy plus stable protocol and status color semantics.
- Flat structural layers with shallow elevation only where separation or interaction benefits.
- Theme-aware tokens and modular Angular components that preserve established PatternFly contracts.

## Colors

Workbench Blue anchors action and navigation while the Protocol Spectrum makes API type legible at a glance. Neutral
surfaces carry most of the interface so semantic color remains informative.

### Primary

- **Workbench Blue** (`workbench-blue`): links, primary actions, active tabs, chart bars, and selected states.
- **Deep Workbench Blue** (`workbench-blue-deep`): hover, active, and high-emphasis blue states.

### Secondary

- **REST Green** (`rest-green`): REST protocol rings and labels.
- **SOAP Blue** (`soap-blue`): SOAP protocol rings and selected step indicators.
- **Event Orange** (`event-orange`): event-driven protocol rings, labels, and operation emphasis.
- **gRPC Teal** (`grpc-teal`): gRPC protocol rings and labels.
- **GraphQL Magenta** (`graphql-magenta`): GraphQL protocol rings and labels.
- **Generic Purple** (`generic-purple`): direct and generic API labels.

### Tertiary

- **Success Green** (`success-green`): passing tests and positive result bars.
- **Danger Red** (`danger-red`): failed tests, destructive actions, and critical results.

### Neutral

- **Canvas** (`canvas`) and **Dark Canvas** (`canvas-dark`): primary light and dark application backgrounds.
- **Muted Surface** (`surface-muted`) and **Subtle Surface** (`surface-subtle`): grouped controls, headings, cards, and
	read-only regions.
- **Hover Surface** (`surface-hover`): selected or hovered list rows and interactive regions.
- **Default Text** (`text-default`), **Strong Text** (`text-strong`), and **Muted Text** (`text-muted`): the core content
	hierarchy.
- **Muted Border** (`border-muted`) and **Divider** (`divider`): quiet structural separation.

**The Semantic Color Rule.** Never repurpose a protocol or result color for unrelated decoration. Color is a compact
data channel in Microcks, not ambient ornament.

**The Theme Parity Rule.** Every new semantic token must remain legible and preserve its meaning in both light and dark
themes.

## Typography

**Display Font:** Open Sans (with Helvetica and Arial fallbacks)

**Body Font:** Open Sans (with Helvetica and Arial fallbacks)

**Label/Mono Font:** Open Sans for labels; SFMono-Regular, Menlo, Monaco, Consolas, and system monospace fallbacks for
code and payloads

**Character:** The single sans-serif family keeps dense operational screens calm and familiar. Weight, size, casing,
and spacing establish hierarchy without introducing a decorative display voice; monospace is reserved for machine data.

### Hierarchy

- **Display** (600, `display`, 1.2): dashboard-level emphasis and rare top-level summaries.
- **Headline** (300, `headline`): service identity and large detail-page titles.
- **Title** (400, `title`): page regions, dialogs, and prominent component headings.
- **Body** (400, `body`): forms, descriptions, table and list content.
- **Label** (600, `label`): compact graph labels, control labels, badges, and high-density metadata.
- **Code** (400, `code`): payloads, destinations, identifiers, and other machine-readable values.

**The Operational Type Rule.** Use typography to clarify hierarchy and data type; do not introduce hero-scale or
decorative type into task surfaces.

## Layout

The application uses a fixed PatternFly navigation shell with a fluid content canvas. Pages favor rows, lists, tables,
toolbars, tabsets, and split detail regions over decorative card grids. Content density is deliberate: controls sit near
the data they affect, primary and secondary actions share a clear toolbar or heading region, and repeated records align
for rapid comparison.

Spacing follows a compact working rhythm from `xs` and `sm` for icon, control, and inline gaps through `field` for
component interiors, `section` for page regions, and `spacious` only for empty states or deliberate emphasis. Bootstrap
breakpoints at 480px, 768px, 992px, 1200px, and 1400px progressively reflow filters, dialogs, split views, and wide
containers. At narrow widths, preserve task order, allow labels and graph annotations to wrap, and keep controls usable
rather than merely scaling the desktop composition.

**The Workbench Density Rule.** Prefer one clear, aligned information structure over a field of isolated cards. Add
space to separate jobs, not to make operational screens feel promotional.

## Elevation & Depth

The system is flat by default and lifted when useful. Dividers, borders, and tonal surfaces establish most hierarchy.
Cards use a shallow one-pixel shadow; dropdowns and menus use a stronger but still compact layer; interactive live-trace
nodes gain a small colored shadow on hover. Depth should explain containment, overlap, or interaction rather than decorate
static content.

### Shadow Vocabulary

- **Card Rest** (`0 1px 1px rgba(3, 3, 3, 0.175)`): default PatternFly cards on the light canvas.
- **Card Rest Dark** (`0 1px 1px rgba(3, 3, 3, 0.825)`): equivalent separation on the dark canvas.
- **Floating Menu** (`0 6px 12px rgba(0, 0, 0, 0.175)`): dropdowns and transient overlays.
- **Interactive Node** (`0 2px 8px` to `0 4px 12px` mixed from the node accent): graph nodes at rest and hover.

**The Structural Depth Rule.** Keep surfaces flat until a border, tonal layer, or shallow shadow communicates a real
relationship. Never stack elevated cards inside elevated cards.

## Shapes

Microcks uses crisp, sturdy geometry. PatternFly controls, breadcrumbs, and list containers use nearly square corners
(`crisp`). Dialog internals and compact controls may use `control` or `panel`; newer interactive graph nodes use `node`.
The rounded `chip` silhouette is reserved for short metadata tags, while circles belong to protocol icons, step markers,
and status indicators. Dashed rounded outlines are appropriate for drop targets, not general containers.

**The Radius Has Meaning Rule.** Increasing radius signals a compact tag, node, or direct-manipulation target. Do not
soften ordinary panels and controls with large, interchangeable corners.

## Components

Components are restrained, sturdy, and predictable. Preserve their established Angular inputs, outputs, semantic HTML,
and PatternFly or Bootstrap class contracts when refining their appearance.

### Buttons

- **Shape:** compact, nearly square PatternFly controls (`crisp`) with bold label typography.
- **Primary:** Workbench Blue with white text; reserve it for the main action in a local task region.
- **Default:** neutral canvas with a quiet border for secondary actions.
- **Link:** Workbench Blue without a filled container for low-emphasis or toolbar actions.
- **Hover / Focus:** deepen the semantic color and retain a visible keyboard focus treatment; do not rely on motion alone.
- **Danger / Success:** use semantic variants only when the action or outcome is genuinely destructive or positive.

### Chips

- **Metadata:** subtle neutral fill, quiet border, compact horizontal padding, and the `chip` radius.
- **Protocol:** compact labels use the stable Protocol Spectrum rather than arbitrary per-page colors.
- **State:** maintain readable text contrast and do not use color as the only carrier of meaning.

### Cards / Containers

- **Corner Style:** crisp by default; moderate rounding belongs to interactive graph nodes and upload targets.
- **Background:** canvas or a subtle neutral surface, including theme-aware equivalents.
- **Shadow Strategy:** shallow Card Rest elevation or no shadow; rely first on headings, dividers, and tonal grouping.
- **Internal Padding:** compact and consistent with adjacent lists and toolbars.

### Inputs / Fields

- **Style:** white or theme canvas, muted one-pixel border, compact height, and restrained corner radius.
- **Focus:** use Workbench Blue or the active semantic accent with a visible border or outline.
- **Read-only / Disabled:** shift to Subtle Surface while retaining legible text.
- **Placeholder:** muted and optionally italic; it supplements rather than replaces a persistent label.

### Navigation

- **Style:** fixed PatternFly top and vertical navigation with the Microcks logo, familiar icons, concise labels, and
	persistent access to import, help, identity, administration, and theme controls.
- **State:** active and hover states must be obvious without moving the layout.
- **Responsive:** preserve route access and control labels; collapse according to the established PatternFly shell.

### Lists and Toolbars

- **Style:** full-width aligned records, compact metadata, conventional filters, and actions placed at the trailing edge.
- **State:** use Hover Surface for hover and selection, with borders or labels reinforcing meaning.
- **Behavior:** keep comparison columns aligned and prevent dynamic labels from shifting action positions.

### Live Trace Nodes

- **Style:** protocol- or role-colored borders, pale tonal fills, compact labels, and shallow accent-tinted shadows.
- **Behavior:** expansion changes information capacity, not identity; hover and focus strengthen the existing accent.
- **Responsive:** wrap supporting text and preserve a usable graph canvas on narrow screens.

## Do's and Don'ts

### Do:

- **Do** use the shared semantic tokens and provide light/dark equivalents instead of scattering literal colors.
- **Do** preserve protocol distinctions and pair color with text, icons, or labels.
- **Do** keep operational pages compact, aligned, and optimized for scanning and repeated action.
- **Do** keep Angular components modular and single-purpose, with component-scoped styles for local behavior.
- **Do** preserve established PatternFly selectors and component APIs unless a migration explicitly owns the break.

### Don't:

- **Don't** turn app screens into marketing compositions with oversized heroes, decorative storytelling, or sparse copy.
- **Don't** collapse REST, SOAP, event, gRPC, GraphQL, and generic APIs into a monochrome treatment.
- **Don't** use deep shadows, oversized radii, or nested cards as default structure.
- **Don't** introduce one-off colors, typefaces, or spacing values when an existing semantic token fits.
- **Don't** sacrifice keyboard focus, text wrapping, or dark-theme legibility for visual polish.
