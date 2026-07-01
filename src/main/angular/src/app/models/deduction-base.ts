/**
 * The base a percentage deduction or variable applies its rate to. These are the tokens the `base`
 * field on the `Deduction` and `Variable` interfaces carries. Values are the JSON wire tokens and
 * match the backend `DeductionBase` 1:1.
 */
export enum DeductionBase {
  /** Total gross pay. */
  Gross = 'deductionBase.gross',
  /** The "basic" pay figure (falls back to gross when no component is flagged basic). */
  Basic = 'deductionBase.basic',
  /** Taxable gross minus the pretax deductions applied so far. */
  Taxable = 'deductionBase.taxable',
  /** Gross times 12. */
  Annual = 'deductionBase.annual',
  /** A named scope variable, chosen by the companion `baseVar`. */
  Var = 'deductionBase.var',
}
