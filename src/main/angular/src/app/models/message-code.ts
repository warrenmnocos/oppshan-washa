// TS MessageCode — superset of the Java enum (which holds only backend-emitted error codes).
// Frontend-only info/UI codes never cross the wire. Values are i18n key paths in en.json.

export enum MessageCode {
  // --- backend-mirrored (byte-equal with Java) ---
  Unknown = 'messages.errors.unknown',
  AuthenticationRequired = 'messages.errors.authenticationRequired',
  AccessDenied = 'messages.errors.accessDenied',
  SignInFailed = 'messages.errors.signInFailed',
  UserNotFound = 'messages.errors.userNotFound',

  // --- frontend-only info toasts ---
  SignInSucceeded = 'messages.info.signInSucceeded',
  BudgetSaved = 'messages.info.budgetSaved',
  MonthLoaded = 'messages.info.monthLoaded',
  ImportSucceeded = 'messages.info.importSucceeded',
  FxRefreshed = 'messages.info.fxRefreshed',

  // --- client-side validation ---
  ImportRejected = 'messages.errors.importRejected',
}

export function messageCodeOf(value: string | null | undefined): MessageCode {
  if (!value) {
    return MessageCode.Unknown;
  }
  const match = Object.values(MessageCode).find((code) => code === value);
  return (match as MessageCode) ?? MessageCode.Unknown;
}
