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
     *
     * Rounding is half away from zero, so -2.5 shows as -3 just as 2.5 shows as 3 (Math.round alone takes
     * -2.5 to -2). The magnitude is first cut to 15 significant digits, all a double reliably holds: that
     * clears binary noise sitting just under a half (0.35 / 0.1 is 3.4999999999999996, which would show
     * as 3 instead of 4). A figure that rounds to zero shows as 0, never -0.
     */
    transform(amount: number | null | undefined, currency?: Currency | string | null): string {
        const value = amount ?? 0;
        const magnitude = Math.round(Number(Math.abs(value).toPrecision(15)));
        const rounded = magnitude === 0 ? 0 : Math.sign(value) * magnitude;
        const grouped = rounded.toLocaleString('en-US');
        const symbol = typeof currency === 'string'
            ? (CURRENCY_SYMBOLS[currency] ?? currency)
            : currency?.sym ?? '';
        return `${symbol}${grouped}`;
    }
}
