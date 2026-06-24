// A goal's target kind. Values are the JSON wire strings and match the backend GoalTargetType /
// Java Goal.TargetType 1:1.
export enum GoalTargetType {
  Open = 'open',
  Amount = 'amount',
  Relative = 'relative',
}
