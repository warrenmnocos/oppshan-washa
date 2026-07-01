/**
 * How a bracket-table row contributes once its comparison holds. Values are the JSON wire tokens and
 * match the backend `BracketType` 1:1.
 */
export enum BracketType {
  /** A flat amount: the row's `rate` is used directly, not as a percentage. */
  Fixed = 'bracketType.fixed',
  /** Evaluates the row's formula (`expr`) against the salary scope. */
  Formula = 'bracketType.formula',
  /** `rate` percent of gross pay. */
  PctGross = 'bracketType.pctgross',
  /** `rate` percent of basic pay. */
  PctBasic = 'bracketType.pctbasic',
}
