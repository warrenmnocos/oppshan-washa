/**
 * Best-effort currency glyphs keyed by ISO 4217 code, so an amount can show its symbol (¥, ₱, $, …)
 * even for a currency a month hasn't given a symbol of its own. Any code not listed falls back to the
 * bare code string. Several glyphs repeat by design (JPY and CNY both render ¥; a handful map to `$`),
 * so this resolves a display glyph, not a unique currency.
 */
export const CURRENCY_SYMBOLS: Record<string, string> = {
  USD: '$', EUR: '€', GBP: '£', JPY: '¥', CNY: '¥', PHP: '₱', KRW: '₩', INR: '₹', THB: '฿',
  VND: '₫', IDR: 'Rp', MYR: 'RM', SGD: 'S$', HKD: 'HK$', TWD: 'NT$', AUD: 'A$', CAD: 'C$',
  NZD: 'NZ$', CHF: 'Fr', SEK: 'kr', NOK: 'kr', DKK: 'kr', RUB: '₽', BRL: 'R$', MXN: 'Mex$',
  ZAR: 'R', TRY: '₺', AED: 'د.إ', SAR: '﷼', QAR: 'ر.ق', ILS: '₪', PLN: 'zł', CZK: 'Kč',
  HUF: 'Ft', RON: 'lei', BDT: '৳', PKR: '₨', LKR: 'Rs', NPR: 'Rs', MMK: 'K', KHR: '៛',
  LAK: '₭', BND: 'B$', MOP: 'MOP$', MNT: '₮', KZT: '₸', UAH: '₴', NGN: '₦', EGP: 'E£',
  KES: 'KSh', GHS: '₵', COP: '$', CLP: '$', ARS: '$', PEN: 'S/', UYU: '$U',
};
