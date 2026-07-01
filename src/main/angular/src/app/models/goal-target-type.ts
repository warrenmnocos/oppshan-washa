/**
 * A goal's target kind; it's the discriminator of the `GoalTarget` union. Values are the JSON wire
 * tokens and match the backend `GoalTargetType` 1:1.
 */
export enum GoalTargetType {
  /** Open-ended: no target figure. */
  Open = 'goalTargetType.open',
  /** A fixed amount. */
  Amount = 'goalTargetType.amount',
  /** A multiple of net income. */
  Relative = 'goalTargetType.relative',
  /** A deadline: an explicit due date, or a period from the goal's start. */
  Time = 'goalTargetType.time',
}
