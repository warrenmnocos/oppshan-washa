// How a salary custom variable is computed. Values are the JSON wire strings (the `kind` field) and
// match the backend VariableType 1:1.
export enum VariableType {
  Pct = 'pct',
  Fixed = 'fixed',
  Formula = 'formula',
  Brackets = 'brackets',
}
