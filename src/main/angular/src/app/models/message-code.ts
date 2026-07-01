/**
 * The frontend's half of the `MessageCode` contract (root CLAUDE.md C.1). Each value is an i18n key
 * path resolved against `en.json`, and severity comes from the prefix (`messages.errors.*` = error,
 * `messages.info.*` = info). This enum is the superset: it mirrors the Java `MessageCode` byte-for-byte
 * for every code the backend emits, then adds frontend-only codes that never cross the wire.
 */
export enum MessageCode {
  /**
   * Backend-mirrored codes: every value from here through `SalaryPresetBuiltIn` is byte-equal with a
   * Java `MessageCode` constant, so the wire contract holds. `Unknown` (the fallback for an
   * unrecognized code) and `SignInFailed` keep Java counterparts for alignment even though no current
   * backend path emits them.
   */
  Unknown = 'messages.errors.unknown',
  AuthenticationRequired = 'messages.errors.authenticationRequired',
  AccessDenied = 'messages.errors.accessDenied',
  SignInFailed = 'messages.errors.signInFailed',
  UserNotFound = 'messages.errors.userNotFound',
  SalaryPresetNotFound = 'messages.errors.salaryPresetNotFound',
  SalaryPresetBuiltIn = 'messages.errors.salaryPresetBuiltIn',

  /**
   * Frontend-only success and lifecycle codes; the backend never emits these, so Java has no
   * counterpart (a Java entry would be a dead enum value).
   */
  SignInSucceeded = 'messages.info.signInSucceeded',
  BudgetSaved = 'messages.info.budgetSaved',
  MonthLoaded = 'messages.info.monthLoaded',
  ImportSucceeded = 'messages.info.importSucceeded',
  FxRefreshed = 'messages.info.fxRefreshed',

  /** Frontend-only validation code (error severity), raised client-side before any request goes out. */
  ImportRejected = 'messages.errors.importRejected',
}

/**
 * Resolves a raw wire string to its `MessageCode`, falling back to `Unknown` when the value is
 * missing or unrecognized, so a code that has drifted out of sync degrades gracefully rather than
 * throwing.
 */
export function messageCodeOf(value: string | null | undefined): MessageCode {
  if (!value) {
    return MessageCode.Unknown;
  }
  const match = Object.values(MessageCode).find((code) => code === value);
  return (match as MessageCode) ?? MessageCode.Unknown;
}
