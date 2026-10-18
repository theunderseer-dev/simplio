"""Run from the Simplio repository after copying the supplied Kotlin files.

Updates integration points whose real filenames were not provided, plus existing
registration test selectors. All edits are checked in memory before any are saved.
Use --check to print the proposed file list without writing it.
"""

from pathlib import Path
import re
import sys


def closing(source, start):
    opener = source[start]
    closer = {'(': ')', '{': '}'}[opener]
    depth = 0
    tokens = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/|[(){}]')
    for match in tokens.finditer(source, start):
        token = match.group()
        if token == opener:
            depth += 1
        elif token == closer:
            depth -= 1
            if depth == 0:
                return match.start()
    raise ValueError('Unbalanced Kotlin expression')


def add_import(source, name):
    line = f'import {name}'
    if line in source.splitlines():
        return source
    imports = list(re.finditer(r'^import .+$', source, re.M))
    if not imports:
        pos = source.index('\n') + 1
        return source[:pos] + '\n' + line + '\n' + source[pos:]
    pos = imports[-1].end()
    return source[:pos] + '\n' + line + source[pos:]


def append_parameter(source, pattern, parameter, already):
    match = re.search(pattern, source)
    if not match:
        raise ValueError(f'Missing declaration: {pattern}')
    start = source.index('(', match.start())
    end = closing(source, start)
    body = source[start + 1:end]
    if already in body:
        return source
    body = body.rstrip().rstrip(',')
    return source[:start + 1] + body + ',\n    ' + parameter + ',\n' + source[end:]


def main():
    root = Path.cwd()
    source_root = root / 'feature/attendance/src'
    if not source_root.is_dir():
        raise ValueError('Run this script from the Simplio repository root')
    paths = sorted(source_root.rglob('*.kt'))
    original = {p: p.read_text() for p in paths}
    edited = dict(original)

    def unique(pattern):
        matches = [p for p, text in edited.items() if re.search(pattern, text)]
        if len(matches) != 1:
            raise ValueError(f'Expected one file matching {pattern}; found: {matches}')
        return matches[0]

    # Locate the actual mapper filename instead of adding a second toDomain function.
    mapper = unique(r'fun\s+StudentEntity\.toDomain\s*\(')
    text = edited[mapper]
    begin = re.search(r'fun\s+StudentEntity\.toDomain\s*\(', text).start()
    match = re.search(r'\bStudent\s*\(', text[begin:])
    if not match:
        raise ValueError(f'Cannot locate Student construction in {mapper}')
    start = text.index('(', begin + match.start())
    end = closing(text, start)
    body = text[start + 1:end]
    if not re.search(r'\bnote\s*=', body):
        body, count = re.subn(r'(groupId\s*=\s*groupId\s*,?)',
                              lambda m: m.group().rstrip(',') + ',\n    note = note,', body, count=1)
        if count != 1:
            raise ValueError(f'Expected named groupId mapping in {mapper}')
        edited[mapper] = text[:start + 1] + body + text[end:]

    database = unique(r'abstract\s+class\s+AttendanceDatabase\b')
    text = edited[database]
    if re.search(r'\bversion\s*=\s*5\b', text):
        text = re.sub(r'\bversion\s*=\s*5\b', 'version = 6', text, count=1)
    elif not re.search(r'\bversion\s*=\s*6\b', text):
        raise ValueError(f'Expected database version 5 or 6 in {database}')
    edited[database] = text

    factory = unique(r'fun\s+createAttendanceDatabase\s*\(')
    text = edited[factory]
    start = text.index('(', text.index('.addMigrations'))
    end = closing(text, start)
    body = text[start + 1:end]
    if 'AttendanceMigration5To6' not in body:
        body = body.rstrip().rstrip(',') + ',\n        AttendanceMigration5To6,\n    '
        edited[factory] = text[:start + 1] + body + text[end:]

    module = unique(r'\b(?:val|fun)\s+attendanceModule\b')
    text = edited[module]
    if 'UpdateStudentNoteUseCase' not in text:
        text = add_import(text, 'com.theunderseer.simplio.feature.attendance.domain.usecase.UpdateStudentNoteUseCase')
        match = re.search(r'\bmodule\s*\{', text)
        if not match:
            raise ValueError(f'Cannot locate module block in {module}')
        pos = match.end()
        text = text[:pos] + '\n    factory { UpdateStudentNoteUseCase(studentRepository = get()) }\n' + text[pos:]
    edited[module] = text

    fixtures = unique(r'internal\s+object\s+AttendanceTestData\b')
    text = edited[fixtures]
    if 'val studentNote =' not in text:
        text = text.replace('internal object AttendanceTestData {',
                            'internal object AttendanceTestData {\n    val studentNote = "Return 100 THB next time"\n', 1)
    for function, constructor in [('testStudent', 'Student'), ('testStudentEntity', 'StudentEntity')]:
        text = append_parameter(text, rf'fun\s+{function}\s*\(', 'note: String = ""', 'note: String')
        begin = re.search(rf'fun\s+{function}\s*\(', text).start()
        match = re.search(rf'\b{constructor}\s*\(', text[begin:])
        start = text.index('(', begin + match.start())
        end = closing(text, start)
        body = text[start + 1:end]
        if not re.search(r'\bnote\s*=', body):
            body, count = re.subn(r'(groupId\s*=\s*groupId\s*,?)',
                                  lambda m: m.group().rstrip(',') + ',\n    note = note,', body, count=1)
            if count != 1:
                raise ValueError(f'Cannot add note mapping to {function}')
            text = text[:start + 1] + body + text[end:]
    text = append_parameter(text, r'data\s+class\s+StudentRegistrationSubmission\s*\(',
                            'val note: String = ""', 'val note: String')
    text = append_parameter(text, r'fun\s+onRegister\s*\(', 'note: String', 'note: String')
    if 'note = note' not in text[text.index('internal class StudentRegistrationTestCallback'):]:
        needle = 'recordFirstAttendance = recordFirstAttendance,'
        if text.count(needle) != 1:
            raise ValueError('Cannot locate registration submission mapping')
        text = text.replace(needle, needle + '\n            note = note,', 1)
    edited[fixtures] = text

    screen_wrapper = unique(r'fun\s+AttendanceScreenForTest\s*\(')
    text = edited[screen_wrapper]
    text = append_parameter(text, r'fun\s+AttendanceScreenForTest\s*\(',
                            'onUpdateStudentNote: (Int, String) -> Unit = { _, _ -> }', 'onUpdateStudentNote:')
    text = append_parameter(text, r'fun\s+AttendanceScreenForTest\s*\(',
                            'onClearUpdateStudentNoteState: () -> Unit = {}', 'onClearUpdateStudentNoteState:')
    if 'onUpdateStudentNote = onUpdateStudentNote' not in text:
        needle = 'onRetryClassHistory = onRetryClassHistory,'
        if needle not in text:
            raise ValueError('Cannot locate AttendanceScreenForTest forwarding')
        text = text.replace(needle, needle + '\n        onUpdateStudentNote = onUpdateStudentNote,'
                            '\n        onClearUpdateStudentNoteState = onClearUpdateStudentNoteState,', 1)
    edited[screen_wrapper] = text

    # Update all known constructor call sites, including the shared ViewModel test factory.
    for path in paths:
        text = edited[path]
        needs_use_case_import = False
        for match in reversed(list(re.finditer(r'\bAttendanceViewModel\s*\(', text))):
            if re.search(r'\bclass\s*$', text[:match.start()]):
                continue
            start = text.index('(', match.start())
            end = closing(text, start)
            body = text[start + 1:end]
            if 'updateStudentNoteUseCase =' in body:
                continue
            indent_match = re.search(r'\n( +)\w+\s*=', body)
            indent = indent_match.group(1) if indent_match else '        '
            if any('Test' in part for part in path.parts):
                repository = re.search(r'\bstudentRepository\s*=\s*(\w+)\s*[,)]', body)
                if not repository:
                    raise ValueError(f'Cannot resolve StudentRepository variable for ViewModel in {path}')
                expression = f'UpdateStudentNoteUseCase(studentRepository = {repository.group(1)})'
                text = text[:start + 1] + '\n' + indent + f'updateStudentNoteUseCase = {expression},' + body + text[end:]
                needs_use_case_import = True
            else:
                text = text[:start + 1] + '\n' + indent + 'updateStudentNoteUseCase = get(),' + body + text[end:]
        if needs_use_case_import:
            text = add_import(text, 'com.theunderseer.simplio.feature.attendance.domain.usecase.UpdateStudentNoteUseCase')
        edited[path] = text

    callback_type = re.compile(r'\(String,\s*Int,\s*PackageType,\s*LocalDate,\s*Int,\s*Boolean\)\s*->\s*Unit')
    for path in paths:
        text = edited[path]
        text = callback_type.sub('(String, Int, PackageType, LocalDate, Int, Boolean, String) -> Unit', text)
        # Adapt old no-op and custom registration callbacks without touching method references.
        if 'onRegister' in text:
            text = re.sub(r'\{\s*_,\s*_,\s*_,\s*_,\s*_,\s*_\s*->\s*}',
                          '{ _, _, _, _, _, _, _ -> }', text)

            def callback(match):
                params = match.group(2).strip()
                if len(params.split(',')) == 6:
                    return match.group(1) + params + ', _ ->'
                return match.group()

            text = re.sub(r'(onRegister(?:Student)?\s*=\s*\{)([^{}]*?)(->)', callback, text)

        if any('Test' in part for part in path.parts):
            text = re.sub(
                r'(override\s+suspend\s+fun\s+create\s*\(\s*fullName:\s*String,\s*groupId:\s*Int)\s*\)',
                r'\1, note: String)', text,
            )
            for match in reversed(list(re.finditer(r'object\s*:\s*StudentRepository\s*\{', text))):
                start = text.index('{', match.start())
                end = closing(text, start)
                if 'fun updateNote(' not in text[start:end]:
                    indent = re.search(r'(?m)^( *)', text[text.rfind('\n', 0, match.start()) + 1:]).group(1) + '    '
                    stub = '\n' + indent + 'override suspend fun updateNote(studentId: Int, note: String): Boolean =\n' + indent + '    error("Note updates are not used by this test")\n'
                    text = text[:start + 1] + stub + text[start + 1:]

            if 'StudentRegistrationDialogForTest' in text or 'AttendanceScreenForTest' in text:
                text, count = re.subn(r'onNode\(\s*hasSetTextAction\(\)\s*,?\s*\)',
                                      'onNodeWithTag("registration-full-name")', text)
                if count:
                    text = add_import(text, 'androidx.compose.ui.test.onNodeWithTag')
                    if text.count('hasSetTextAction') == 1:
                        text = re.sub(r'^import androidx\.compose\.ui\.test\.hasSetTextAction\n', '', text, flags=re.M)
            if 'StudentRegistrationDialogForTest' in text or re.search(r'class\s+\w*Registration\w*Test\b', text):
                # The additional field can move registration controls below the viewport.
                text, count = re.subn(
                    r'(onNodeWithText\("(?:Record first attendance|People attending|1 person|2 people)"\))'
                    r'(\s*)(\.(?:assertIsDisplayed|assertIsOn|assertIsOff|assertIsSelected|assertIsNotSelected|performClick)\b)',
                    r'\1.performScrollTo()\2\3', text,
                )
                if count:
                    text = add_import(text, 'androidx.compose.ui.test.performScrollTo')
        edited[path] = text

    changes = {p: text for p, text in edited.items() if text != original[p]}
    for path in changes:
        print(path.relative_to(root))
    if '--check' in sys.argv:
        print(f'Preflight passed: {len(changes)} files would change; no files written')
    else:
        for path, text in changes.items():
            path.write_text(text)
        print(f'Updated {len(changes)} integration files. Review git diff and run Gradle checks.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError) as error:
        print(f'No integration edits saved: {error}', file=sys.stderr)
        sys.exit(1)
