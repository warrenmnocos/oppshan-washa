import {Pipe, PipeTransform} from '@angular/core';
import {Currency} from '../models/budget.models';
import {CURRENCY_SYMBOLS} from '../models/currency-symbols';

/** Formats an amount as whole base units with grouping and the currency symbol (mockup money()). */
@Pipe({name: 'money', standalone: true})
export class MoneyPipe implements PipeTransform {

  /**
   * Round to whole units, group with en-US thousands separators, and prefix the currency symbol. A
   * string argument is a currency CODE, resolved to its glyph (¥, ₱, …) via CURRENCY_SYMBOLS (falling
   * back to the code itself) so a month with no saved currency settings still shows symbols; an object
   * argument carries its own sym; null or undefined yields no symbol.
   */
  transform(amount: number | null | undefined, currency?: Currency | string | null): string {
    const value = amount ?? 0;
    const rounded = Math.round(value);
    const grouped = rounded.toLocaleString('en-US');
    const symbol = typeof currency === 'string'
      ? (CURRENCY_SYMBOLS[currency] ?? currency)
      : currency?.sym ?? '';
    return `${symbol}${grouped}`;
  }
}
