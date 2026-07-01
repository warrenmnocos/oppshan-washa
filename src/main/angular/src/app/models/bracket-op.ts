/**
 * The comparison a bracket-table row runs: it tests the row's left-hand scope value against its
 * threshold (a `Bracket`'s `val`), and contributes only when the test holds. Values are the JSON wire
 * tokens and match the backend `BracketOp` 1:1 (the Java constant's `@JsonValue`), so the same string
 * round-trips both directions.
 */
export enum BracketOp {
  Gt = 'bracketOp.gt',
  Gte = 'bracketOp.gte',
  Lt = 'bracketOp.lt',
  Lte = 'bracketOp.lte',
  Eq = 'bracketOp.eq',
}
