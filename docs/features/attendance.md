# Attendance

## Goal

The Attendance feature reduces the manual work required to track students,
class attendance, and package usage after recurring group classes.

After a scheduled class, Simplio should make it quick to record attendance,
update package state, and preserve the result for the next session.

Attendance must also be accessible manually without waiting for a reminder.

## Core Behavior

### Register a new student

```gherkin
Scenario: Register a new student during today's class
  Given today's class session is open
  And a package type is available
  When I register a student with that package
  Then the student should be added to the class roster
  And the package should start today
  And today's attendance should be recorded automatically
  And the package should show 1 used class
  And its expiration date should be calculated from the package configuration
```

### Record attendance

```gherkin
Scenario: Record attendance for an existing student
Given a student has an active package
And the student has not attended today's class yet
When I mark the student as present
Then today's attendance should be added to the package history
And the used class count should increase by one
```

### Prevent duplicate attendance

```gherkin
Scenario: Do not record attendance twice for the same class
  Given the student is already marked present for today's class
  When I mark the student as present again
  Then today's attendance should exist only once
  And the used class count should not increase
```

### Complete a package

```gherkin
Scenario: Use the final class of a package
  Given the student has one remaining class
  When I mark the student as present
  Then the package should become fully used
  And it should require renewal
  And the student should remain in the class roster
```

### View attendance history

```gherkin
Scenario: View attendance history
  Given the student has attended multiple class sessions
  When I request the attendance history for the current package
  Then I should see the dates of those attended sessions
```

### Open Attendance from a reminder

```gherkin
Scenario: Open Attendance from a scheduled reminder
  Given an attendance reminder is shown
  When I open the reminder
  Then Attendance should open for the relevant class session
```

### Open Attendance manually

```gherkin
Scenario: Open Attendance without a reminder
  Given Simplio is running
  When I open the Attendance feature manually
  Then I should be able to access the current attendance state
```

## Package Rules

Package types are configuration rather than hardcoded feature behavior.
A package type defines:
- number of classes;
- price;
- validity period. 

The initial MVP will support predefined 4-class and 5-class packages.
  When a package is assigned to a student, the conditions of that purchase
  should remain stable even if the package type is changed later.

## MVP Scope
### Included:
- student registration;
- predefined 4-class and 5-class packages;
- automatic attendance when registering a new student;
- attendance tracking;
- prevention of duplicate attendance;
- package usage tracking;
- package expiration;
- attendance history;
- renewal indication;
- scheduled reminder;
- manual access to Attendance;
- persistent attendance state;
- copyable attendance summary.

### Deferred:
- custom packages;
- manual expiration adjustments;
- extensions for cancelled or rescheduled classes;
- referrals and bonuses;
- shared packages;
- custom student ordering;
- remote synchronization;
- mobile implementation.

