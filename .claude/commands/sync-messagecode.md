Check that the MessageCode contract is in sync across all three locations:

1. Read `src/main/java/com/oppshan/washa/exception/MessageCode.java` — extract all enum constant string values.
2. Read `src/main/angular/src/app/models/message-code.ts` — extract all enum constant string values.
3. Read `src/main/angular/public/i18n/en.json` — extract all key paths under `messages.*`.

Then report:
- Any Java MessageCode value missing from the TS enum.
- Any TS MessageCode value missing from the Java enum (flag as frontend-only info codes — these are acceptable if they represent success outcomes the backend never emits).
- Any MessageCode value (Java or TS) whose key path is missing or empty in en.json.
- Any en.json `messages.*` key that has no corresponding MessageCode enum entry.

Output a concise table with status: OK / MISSING / MISMATCH for each value.