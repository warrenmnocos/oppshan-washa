// How a tax bracket row contributes. Values are the JSON wire strings and match the backend
// BracketType 1:1.
export enum BracketType {
  Fixed = 'bracketType.fixed',
  Formula = 'bracketType.formula',
  PctGross = 'bracketType.pctgross',
  PctBasic = 'bracketType.pctbasic',
}
