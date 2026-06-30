import {Pipe, PipeTransform} from '@angular/core';
import {Currency} from '../models/budget.models';
import {CURRENCY_SYMBOLS} from '../models/currency-symbols';

/** Formats an amount as whole base units with grouping and the currency symbol (mockup money()). */
@Pipe({name: 'money', standalone: true})
export class MoneyPipe implements PipeTransform {

  transform(amount: number | null | undefined, currency?: Currency | string | null): string {
    const value = amount ?? 0;
    const rounded = Math.round(value);
    const grouped = rounded.toLocaleString('en-US');
    // A string argument is a currency CODE: resolve it to its glyph (¥, ₱, …) so a month with no
    // saved currency settings still shows symbols, not bare codes. An object carries its own symbol.
    const symbol = typeof currency === 'string'
      ? (CURRENCY_SYMBOLS[currency] ?? currency)
      : currency?.sym ?? '';
    return `${symbol}${grouped}`;
  }
}
