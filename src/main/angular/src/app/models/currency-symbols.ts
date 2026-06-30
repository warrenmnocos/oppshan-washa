// Best-effort currency glyphs by ISO code, so an amount renders with its symbol (¥, ₱, $, …) even
// when the working month carries no currency settings of its own. Anything not listed falls back to
// the bare code. Single source of truth for the money pipe and the budget page.
export const CURRENCY_SYMBOLS: Record<string, string> = {
  USD: '$', EUR: '€', GBP: '£', JPY: '¥', CNY: '¥', PHP: '₱', KRW: '₩', INR: '₹', THB: '฿',
  VND: '₫', IDR: 'Rp', MYR: 'RM', SGD: 'S$', HKD: 'HK$', TWD: 'NT$', AUD: 'A$', CAD: 'C$',
  NZD: 'NZ$', CHF: 'Fr', SEK: 'kr', NOK: 'kr', DKK: 'kr', RUB: '₽', BRL: 'R$', MXN: 'Mex$',
  ZAR: 'R', TRY: '₺', AED: 'د.إ', SAR: '﷼', QAR: 'ر.ق', ILS: '₪', PLN: 'zł', CZK: 'Kč',
  HUF: 'Ft', RON: 'lei', BDT: '৳', PKR: '₨', LKR: 'Rs', NPR: 'Rs', MMK: 'K', KHR: '៛',
  LAK: '₭', BND: 'B$', MOP: 'MOP$', MNT: '₮', KZT: '₸', UAH: '₴', NGN: '₦', EGP: 'E£',
  KES: 'KSh', GHS: '₵', COP: '$', CLP: '$', ARS: '$', PEN: 'S/', UYU: '$U',
};
