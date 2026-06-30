// How a salary custom variable is computed. Values are the JSON wire strings (the `kind` field) and
// match the backend VariableType 1:1.
export enum VariableType {
  Pct = 'variableType.pct',
  Fixed = 'variableType.fixed',
  Formula = 'variableType.formula',
  Brackets = 'variableType.brackets',
}
