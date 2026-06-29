# Diagram conventions

Conventions for hand-drawn SVG diagrams under `docs/diagrams/`. These mirror the house style used across the sibling oppshan projects so the diagrams read consistently.

## Connections (every diagram)

- **Orthogonal lines only.** Vertical `V` and horizontal `H` segments. No diagonal `<line>` (where x1≠x2 ∧ y1≠y2). No Bezier `Q`/`C`/`S`/`A`/`T` curves where an L- or C-shape will route through. Self-loops are right-angle C-shapes, not arcs.
- **Hand-drawn markers.** `markerUnits="userSpaceOnUse"` so size doesn't bloat with stroke width; the marker `viewBox` matches `markerWidth`/`markerHeight` to avoid non-uniform scaling; `refX` at the right edge so the marker stays on the line side of the box. One arrow marker per stroke colour (`arr` gray, `arrRed` for fallback/stop edges), `orient="auto-start-reverse"` so a single definition serves both ends.

## ERD specifics

- **Crow's foot** at every endpoint, with the four marker types `cf-one-one`, `cf-zero-one`, `cf-one-many`, `cf-zero-many`. `orient="auto-start-reverse"` lets one definition serve both `marker-start` and `marker-end`.
- **FK → PK direction.** The arrow tail starts on the entity owning the foreign-key column; the head lands on the entity owning the referenced PK. Reads "this column references that one" (database-tooling convention).
- **Cardinality labels (`1`, `N`) sit OUTSIDE entity boxes** — never overlapping a header. **Verb labels** at line midpoints in italic 9pt, active voice (`belongs to`, `extends`, `roots at`). Repeated verbs are fine; don't mix voices to avoid them.

## Common across all diagrams

- **Title at the top.** `<text x="CENTER" y="24" text-anchor="middle" font-size="13" font-weight="700" fill="#212121">Title</text>`, with a `font-size="9"` `#90A4AE` subtitle on the next line.
- **Background** `#FAFAFA`. Root `<svg>` carries `font-family="Segoe UI, system-ui, -apple-system, sans-serif"`.
- **Semantic colour palette** (role, not brand — shared with the sibling diagrams so they cross-read):

  | Role | Fill | Stroke | Ink |
  |---|---|---|---|
  | Infra / AWS / entry | `#E8F8F5` | `#1ABC9C` | `#0E6655` |
  | Build / native-image | `#FDEBD0` | `#E67E22` | `#784212` |
  | Optimized / result / success | `#D5F5E3` | `#27AE60` | `#145A32` |
  | Setup / lifecycle / readiness | `#D6EAF8` | `#2980B9` | `#1A5276` |
  | Load test / profile collection | `#F4ECF7` | `#7D3C98` | `#512E5F` |
  | Cleanup / utility / existing | `#ECEFF1` | `#546E7A` | `#263238` |
  | Fallback / conditional stop | `#FADBD8` | `#C0392B` (dashed) | `#641E16` |

- **Dashed group containers** (`fill="none"`, `stroke-dasharray="6 4"`) wrap a phase or subsystem, with a small `font-size="9"` label at the top-left. Commands render in `font-family="monospace"`. A legend (dashed container, 14×10 swatches) sits at the bottom.

## Verification

After any edit, scan for zero diagonal `<line>` elements and zero curve commands (`Q|C|S|A|T`) in path `d` attributes, then render to PNG and look at it — coordinate math doesn't catch marker scaling, label-vs-line overlap, or marker skew:

```bash
google-chrome --headless --disable-gpu --no-sandbox \
  --window-size=1000,1180 --hide-scrollbars \
  --screenshot=/tmp/diagram.png file:///abs/path/to.svg
```

Avoid ImageMagick `convert` for SVG-to-PNG — weak SVG support, breaks `markerUnits="userSpaceOnUse"`, and garbles multi-line text. Chrome headless mirrors what a browser renders.
