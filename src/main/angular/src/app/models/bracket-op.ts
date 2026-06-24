// The comparison a tax bracket row applies. Values are the JSON wire strings and match the backend
// BracketOp 1:1.
export enum BracketOp {
  Gt = 'gt',
  Gte = 'gte',
  Lt = 'lt',
  Lte = 'lte',
  Eq = 'eq',
}
