// The base a percentage deduction/variable applies to. Values are the JSON wire strings and match
// the backend 1:1.
export enum DeductionBase {
  Gross = 'deductionBase.gross',
  Basic = 'deductionBase.basic',
  Taxable = 'deductionBase.taxable',
  Annual = 'deductionBase.annual',
  Var = 'deductionBase.var',
}
