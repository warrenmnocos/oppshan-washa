// The base a percentage deduction/variable applies to. Values are the JSON wire strings and match
// the backend 1:1.
export enum DeductionBase {
  Gross = 'gross',
  Basic = 'basic',
  Taxable = 'taxable',
  Annual = 'annual',
  Var = 'var',
}
