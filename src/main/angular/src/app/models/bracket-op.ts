// The comparison a tax bracket row applies. Values are the JSON wire strings and match the backend
// BracketOp 1:1.
export enum BracketOp {
  Gt = 'bracketOp.gt',
  Gte = 'bracketOp.gte',
  Lt = 'bracketOp.lt',
  Lte = 'bracketOp.lte',
  Eq = 'bracketOp.eq',
}
