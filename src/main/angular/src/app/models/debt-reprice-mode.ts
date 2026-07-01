/**
 * How a debt reprices when its rate changes. Values are the JSON wire tokens and match the backend
 * `DebtRepriceMode` 1:1.
 */
export enum DebtRepriceMode {
  /** Re-amortize: recompute the monthly payment and keep the term. */
  Payment = 'debtRepriceMode.payment',
  /** Keep the payment and let the term stretch (or shrink) instead. */
  Term = 'debtRepriceMode.term',
}
