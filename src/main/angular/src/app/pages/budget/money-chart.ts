import {Component, computed, input} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {MoneyPipe} from '../../services/money.pipe';

export interface ChartSlice {
  label: string;
  value: number;
  color: string;
}

interface RenderedSlice extends ChartSlice {
  path: string;
  share: number;
}

const RADIUS = 62;
const INNER = 38;
const CENTER = 80;

@Component({
  selector: 'app-money-chart',
  standalone: true,
  imports: [MoneyPipe, TranslatePipe],
  templateUrl: './money-chart.html',
  styleUrl: './money-chart.scss',
})
export class MoneyChart {

  readonly slices = input.required<ChartSlice[]>();
  readonly baseSymbol = input('¥');

  readonly total = computed(() => this.slices().reduce((sum, slice) => sum + Math.max(0, slice.value), 0));

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

  private arc(start: number, end: number): string {
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
