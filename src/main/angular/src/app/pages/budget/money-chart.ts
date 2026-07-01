import {Component, computed, input, signal} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {MoneyPipe} from '../../services/money.pipe';
import {ChartType} from '../../models/chart-type';

/** One input segment: its label, raw value, and fill color. */
export interface ChartSlice {
  label: string;
  value: number;
  color: string;
}

/** A donut slice: a ChartSlice plus its SVG arc path and share of the total. */
interface RenderedSlice extends ChartSlice {
  path: string;
  share: number;
}

/** A horizontal-bar slice: a ChartSlice plus its width as a percentage of the widest slice. */
interface BarSlice extends ChartSlice {
  width: number;
}

/** A stacked-flow segment: a ChartSlice plus its width as a percentage of the total. */
interface FlowSlice extends ChartSlice {
  width: number;
}

/**
 * Donut geometry in the prototype's 200-unit viewBox (see money-chart.html). Same-space coordinates
 * render the 26px center percentage at the prototype's size instead of magnifying it: a smaller
 * viewBox blows the text up and crowds the ring.
 */
const RADIUS = 77.5;
const INNER = 47.5;
const CENTER = 100;

/**
 * Renders a set of money slices three ways: a donut, horizontal bars, or a single stacked flow bar,
 * switchable via the chart-type tabs. Each layout's geometry is derived from the slice values, and it
 * flags when the allocated slices exceed money-in (over budget). Read-only: it computes from its
 * inputs and holds no domain state beyond the selected chart type. Mirrors the prototype's chart panel.
 */
@Component({
  selector: 'app-money-chart',
  standalone: true,
  imports: [MoneyPipe, TranslatePipe],
  templateUrl: './money-chart.html',
  styleUrl: './money-chart.scss',
})
export class MoneyChart {

  /** The segments to chart (label, value, color); non-positive values are dropped before rendering. */
  readonly slices = input.required<ChartSlice[]>();
  /** The base-currency symbol for the center and total figures (defaults to ¥). */
  readonly baseSymbol = input('¥');
  /** The savings rate shown alongside the chart. */
  readonly savingsRate = input<number>(0);
  /** Total money-in; the over-budget check needs it, since the slices alone can't reveal a shortfall. */
  readonly moneyIn = input<number>(0);

  /** Exposed for template comparisons against the chart-type tabs (frontend convention B.3). */
  readonly Chart = ChartType;
  /** The selected chart layout; defaults to bars, matching the prototype (its Bars tab is pressed on load). */
  readonly chartType = signal<ChartType>(ChartType.Bars);

  /** Sum of the slice values, clamping each negative to 0. */
  readonly total = computed(() => this.slices().reduce((sum, slice) => sum + Math.max(0, slice.value), 0));

  /**
   * True when the allocated slices exceed money-in. Free cash is already clamped to zero upstream, so
   * the slices alone can't reveal the shortfall; moneyIn does.
   */
  readonly overBudget = computed(() => this.moneyIn() > 0 && this.total() > this.moneyIn());

  /** How much the slices overshoot money-in, floored at 0. */
  readonly overBudgetBy = computed(() => Math.max(0, this.total() - this.moneyIn()));

  /** The donut slices: positive values only, each turned into an SVG arc plus its share of the total, sweeping from 12 o'clock. */
  readonly rendered = computed<RenderedSlice[]>(() => {
    const slices = this.slices().filter((slice) => slice.value > 0);
    const total = slices.reduce((sum, slice) => sum + slice.value, 0);
    if (total <= 0) {
      return [];
    }

    let angle = -Math.PI / 2;
    return slices.map((slice) => {
      const share = slice.value / total;
      const end = angle + share * 2 * Math.PI;
      const path = this.arc(angle, end);
      angle = end;
      return {...slice, path, share};
    });
  });

  /** Horizontal bars, one per slice, each scaled to the widest slice (the prototype's barHTML). */
  readonly bars = computed<BarSlice[]>(() => {
    const slices = this.slices().filter((slice) => slice.value > 0);
    const max = slices.reduce((peak, slice) => Math.max(peak, slice.value), 0);
    return slices.map((slice) => ({...slice, width: max > 0 ? (slice.value / max) * 100 : 0}));
  });

  /** A single stacked bar; each segment's width is its share of the total (the prototype's flowHTML). */
  readonly flow = computed<FlowSlice[]>(() => {
    const slices = this.slices().filter((slice) => slice.value > 0);
    const total = slices.reduce((sum, slice) => sum + slice.value, 0);
    return slices.map((slice) => ({...slice, width: total > 0 ? (slice.value / total) * 100 : 0}));
  });

  /** Switch the active chart layout. */
  setChartType(type: ChartType): void {
    this.chartType.set(type);
  }

  /**
   * Build the SVG path for one donut segment between two angles (radians), setting the large-arc flag
   * once the sweep passes a half turn.
   */
  private arc(start: number,
              end: number): string {
    const large = end - start > Math.PI ? 1 : 0;
    const x1 = CENTER + RADIUS * Math.cos(start);
    const y1 = CENTER + RADIUS * Math.sin(start);
    const x2 = CENTER + RADIUS * Math.cos(end);
    const y2 = CENTER + RADIUS * Math.sin(end);
    const ix2 = CENTER + INNER * Math.cos(end);
    const iy2 = CENTER + INNER * Math.sin(end);
    const ix1 = CENTER + INNER * Math.cos(start);
    const iy1 = CENTER + INNER * Math.sin(start);
    return `M ${x1} ${y1} A ${RADIUS} ${RADIUS} 0 ${large} 1 ${x2} ${y2}`
      + ` L ${ix2} ${iy2} A ${INNER} ${INNER} 0 ${large} 0 ${ix1} ${iy1} Z`;
  }
}
