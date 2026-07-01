/**
 * How a salary custom variable is computed. The four kinds mirror `DeductionType` exactly and mean
 * the same thing; values are the JSON wire tokens on a variable's `type` field (the backend also
 * accepts the `kind` alias on input) and match the backend `VariableType` 1:1.
 */
export enum VariableType {
  /** Percentage `rate` of a `base`. */
  Pct = 'variableType.pct',
  /** A flat `amount`. */
  Fixed = 'variableType.fixed',
  /** Evaluates a formula (`expr`). */
  Formula = 'variableType.formula',
  /** Sums a bracket table (`brackets`). */
  Brackets = 'variableType.brackets',
}
