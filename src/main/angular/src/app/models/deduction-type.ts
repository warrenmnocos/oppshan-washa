// How a salary deduction (or custom variable) is computed. Values are the JSON wire strings (the
// `kind` field) and match the backend DeductionType 1:1.
export enum DeductionType {
  Pct = 'deductionType.pct',
  Fixed = 'deductionType.fixed',
  Formula = 'deductionType.formula',
  Brackets = 'deductionType.brackets',
}
