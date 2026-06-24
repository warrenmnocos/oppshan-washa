// How a tax bracket row contributes. Values are the JSON wire strings and match the backend
// BracketType 1:1.
export enum BracketType {
  Fixed = 'fixed',
  Formula = 'formula',
  PctGross = 'pctgross',
  PctBasic = 'pctbasic',
}
