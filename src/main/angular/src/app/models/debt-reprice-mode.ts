// How a debt reprices when its rate changes: re-amortize the payment, or extend the term. Values are
// the JSON wire strings and match the backend 1:1.
export enum DebtRepriceMode {
  Payment = 'debtRepriceMode.payment',
  Term = 'debtRepriceMode.term',
}
