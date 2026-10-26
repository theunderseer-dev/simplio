# Input validation

## Rules

Lengths below use Kotlin UTF-16 string length. The UI cap includes spaces while editing.
Values are checked again before new local writes; existing stored/cloud records are not rewritten by this change.

| Field / form | Accepted input | Value required for confirmation |
| --- | --- | --- |
| Student full name / registration | Up to 200 units on one line; Unicode letters, digits, spaces, `+`, punctuation and symbols allowed; no control characters or Unicode line/paragraph separators | 1–200 units after trimming; no uniqueness restriction |
| Group name / creation and sync rename | Up to 80 units on one line; same character policy as names | Nonempty after normalization; unique ignoring case and collapsed whitespace |
| Package name / creation and sync rename | Up to 80 units on one line; same character policy as names | Nonempty after normalization; unique ignoring case and collapsed whitespace |
| Number of classes / package creation | Up to 10 ASCII digits `0–9`; empty and leading zeros allowed while editing | Integer 1–2147483647 |
| Package price / package creation | Up to 10 ASCII digits `0–9` | Integer 0–2147483647; zero means a free/bonus package; fractional prices are not supported |
| Validity weeks / package creation | Up to 10 ASCII digits `0–9` | Integer 1–306783378 (`Int.MAX_VALUE / 7`); ignored when No expiration is selected, which stores null |
| Starts at, Ends at / new group schedule | Up to five characters; ASCII digits and at most one `:`; partial edits allowed | Exactly `HH:mm`, 00:00–23:59; end strictly later than start on the same day |
| Time zone / new group schedule | Up to 128 ASCII letters/digits or `/ _ + - : .`, without spaces | `kotlinx.datetime.TimeZone.of(value)` must accept the identifier; IANA names, UTC and supported fixed offsets are allowed; abbreviations are not assumed valid |
| Email / sign-in | Up to 254 units; no whitespace/control characters | Basic syntax: one `@`, nonempty local and domain parts; Firebase validates credentials and provider-specific rules |
| Password / sign-in | No added character filter, normalization or length cap | Nonempty; preserve the exact value including spaces; account password policy belongs to Firebase |
| Note / registration, renewal and note editing | Optional free text, including Unicode, digits, symbols and punctuation; no added application length cap; editor remains single-line | May be empty; trim only surrounding whitespace on save |

Student names are trimmed on save. Group/package names are trimmed and repeated whitespace is collapsed;
their duplicate keys are lowercase. Passwords are never trimmed, lowercased or logged.

## Selection controls

The outer date/package fields use pickers and dropdowns. The native DatePicker's input mode keeps its own
locale/date parsing and SelectableDates checks. Existing selection rules remain:

- registration, renewal and attendance follow the selected group's schedule and the existing date boundaries;
- renewal/attendance prevent a second recorded attendance on the same student/date;
- schedule requires at least one weekday, a valid time zone, ordered optional start/end dates and no overlap
  with another schedule; an explicitly shared existing schedule is allowed;
- two-credit attendance requires package capacity of at least two, and actual visits cannot exceed the balance.

## Invalid edits, paste and range errors

- Apply the same input filter to typing and paste. Reject the entire invalid edit and keep the previous value
  and selection. For non-time fields, show a field-level explanation and disable confirmation until an accepted
  edit clears the rejection. Do not turn `-8` into `8`, strip letters, truncate or clamp numbers.
- Time fields quietly ignore edits rejected by their bounded character filter. An unchanged, valid `HH:mm`
  value remains valid and does not block confirmation. A missing colon, `24:00`, `12:60`, or an end time not later
  than the start is an error in the actual current value and still blocks saving. Editing remains ordinary
  insert/selection replacement: no automatic digit overwrite or colon insertion is introduced.
- Empty text is allowed during editing. Required values and ranges still determine whether confirmation is enabled.
- Syntactically accepted values such as `0` classes, `2147483648` or `24:00` remain visible with an error and cannot
  be saved. A valid subsequent edit clears the error. Values from hidden/inactive fields do not block confirmation.
- A valid time-picker choice replaces the time field; confirmation uses the resulting time and schedule rules.
- Free text and passwords are not subjected to numeric/name filters.

## Code ownership

- `shared/validation/InputPolicy.kt`: reusable editing policies and basic email syntax, without UI dependencies.
- `shared/presentation/input/`: state-based Material fields, `InputTransformation`, rejection feedback and cursor handling.
  Transformations change only the text buffer; feedback is delivered through a conflated channel and collected
  outside the transformation. Form confirmation reads the same `TextFieldState` as the editor.
- Attendance domain models/use cases/repositories own required names, numeric ranges, uniqueness and schedule
  invariants. Storage/outbox changes remain inside their existing transactions. No database migration is needed.
- Compose string resources belong to the module displaying the message. Domain/data code does not import UI strings.
- Input filters also apply to user edits made through accessibility/semantics. Programmatic picker updates use
  validated values; they are not a substitute for domain validation.

## Verification

Tests cover rejected replacements, paste-shaped edits, selection preservation, clearing fields, recovery,
integer boundaries, Unicode companion names, optional notes, time ranges, time-zone correction and sign-in.
The same shared field implementation is exercised by the package, group, registration and sync rename forms.

Reference: [Android Developers: Configure text fields](https://developer.android.com/develop/ui/compose/text/user-input).
InputTransformation is the documented filtering mechanism; KeyboardOptions only configures a preferred keyboard.
The concrete lengths and value ranges above are this project's rules, not Android-mandated limits.
