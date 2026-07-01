/**
 * Which allocation chart to render for how a month's money splits across categories. Frontend-only:
 * there's no backend or wire counterpart, so the values are plain tokens (not i18n key paths or
 * Java-mirrored wire tokens) unlike the other enums in this folder.
 */
export enum ChartType {
  /** Bar chart. */
  Bars = 'bars',
  /** Donut chart. */
  Pie = 'pie',
  /** Flow diagram. */
  Flow = 'flow',
}
