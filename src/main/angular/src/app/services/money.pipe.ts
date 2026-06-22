import {Pipe, PipeTransform} from '@angular/core';
import {Currency} from '../models/budget.models';

/** Formats an amount as whole base units with grouping and the currency symbol (mockup money()). */
@Pipe({name: 'money', standalone: true})
export class MoneyPipe implements PipeTransform {

  transform(amount: number | null | undefined, currency?: Currency | string | null): string {
    const value = amount ?? 0;
    const rounded = Math.round(value);
    const grouped = rounded.toLocaleString('en-US');
    const symbol = typeof currency === 'string' ? currency : currency?.sym ?? '';
    return `${symbol}${grouped}`;
  }
}
