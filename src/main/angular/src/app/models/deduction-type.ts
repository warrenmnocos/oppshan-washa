/**
 * How a salary deduction is computed, which also selects the input it reads. Values are the JSON wire
 * tokens carried by a deduction's `type` field (the backend also accepts the mockup's `kind` alias on
 * input) and match the backend `DeductionType` 1:1. The sibling `VariableType` mirrors these four kinds.
 */
export enum DeductionType {
  /** Percentage `rate` of a `base`. */
  Pct = 'deductionType.pct',
  /** A flat `amount`. */
  Fixed = 'deductionType.fixed',
  /** Evaluates a formula (`expr`). */
  Formula = 'deductionType.formula',
  /** Sums a bracket table (`brackets`). */
  Brackets = 'deductionType.brackets',
}
