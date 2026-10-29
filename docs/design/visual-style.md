# Simplio visual style

## Direction and ownership

Silver and graphite surfaces keep attention on attendance work. Green means recorded attendance,
amber means caution, red marks errors, blocked operations, and destructive icon actions. Text, shape,
and accessible state accompany color. The palette and timing are project decisions informed by the
references below; the references do not prescribe these exact colors.

The shared implementation lives in
`shared/src/commonMain/kotlin/com/theunderseer/simplio/shared/presentation/`:

- `theme/SimplioTheme.kt`: Material 3 light/dark schemes, typography, shapes, positive/warning colors.
- `theme/SimplioTokens.kt`: spacing, action/icon sizes, feedback duration.
- `components/SimplioButtons.kt`: primary, outlined and text actions.
- `components/SimplioIconButton.kt`: icon actions and attendance toggles.
- `components/SimplioHoverSurface.kt`: visual row feedback without making the row clickable.
- `components/SimplioVerticalScrollbar.kt` and its JVM actual: visible desktop list scrolling.
- `components/SimplioClickable.kt`: rounded, single-layer feedback for notes, checkbox rows and date overlays.
- `components/SimplioFilterChip.kt`: flat, shared selection controls for groups, weekdays and visit counts.

`DesktopApp` installs `SimplioTheme` once. Sign-in and all dialogs inherit it. Appearance follows
`isSystemInDarkTheme`; previews/tests may explicitly select a theme. Future platform entry points
should install this theme. Reuse these components and semantic colors instead of screen-local
hex colors or independently styled copies. Feature-specific icons remain in feature resources.
`AttendanceColors.warning` delegates to the shared token for existing callers.

## Visual tokens

| Role | Light | Dark |
| --- | --- | --- |
| Page | `#F3F4F6` | `#14171C` |
| Text | `#20252C` | `#E7EBF0` |
| Primary action | `#343D49` | `#D5DCE5` |
| Supporting text | `#555F6C` | `#B3BDCA` |
| Recorded attendance | `#246747` | `#8BD8AF` |
| Warning | `#805400` | `#FFD07A` |
| Error/destructive icon | `#B32636` | `#FFB0B8` |

Use opaque neutral surfaces, fine borders, and spacing to separate content. This first iteration
uses no custom glass/blur layer. Body typography is 16/14/12 sp with 24/21/18 sp line heights, using
the available sans-serif font. No Apple font is bundled.

- Page inset: 24 dp; common spacing: 8, 16, 24 dp.
- Buttons are at least 48 dp high. Standalone icon targets are 48 × 48 dp with a 24 dp glyph.
- Control radius: 10 dp; rows: 14 dp; history cards: 20 dp.
- Main and class-roster Check share their appearance. The main action opens its existing workflow;
  roster Check retains toggle semantics and draft behavior.
- Renew/Delete/Copy/group menu reuse the shared icon component. Delete/Remove icons use the
  destructive tone. Keyboard focus has a contrasting outline; disabled actions remain subdued.
- Main toolbar: Add Student → Add Group → Add Package → group menu. The final pair wraps together.
- Existing proportional student columns, unified scrolling, form fields and validation remain.
- Registration/renewal share their form. Date/package/name/note fields use consistent widths and
  corners. Keep form scrolling available at small window sizes and larger text settings.
- Material pickers, menus, selection controls, and dialogs inherit the same theme roles.

## Focus visibility

Shared action controls and package-history links show focus styling only when
`LocalInputModeManager.current.inputMode == InputMode.Keyboard` and the control has focus.
`collectIsKeyboardFocusedAsState` centralizes that visual policy. Compose owns input-mode
switching, focus restoration, Tab/Shift+Tab traversal, and activation; controls do not call
`clearFocus`, disable focusability, or replace keyboard-accessible clicks with pointer-only handlers.

After a mouse click and pointer exit, action controls return to their normal appearance even
when focus stays on the last activated control. Hover and press feedback remain transient.
Selected chips and recorded attendance retain their selection styling. Editable text fields
keep their native focus/caret feedback, including when entered with the mouse.

`SimplioFocusUiTest` covers mouse focus without residual styling, forward/reverse keyboard
navigation, returning from keyboard to mouse input, and dialog focus restoration in both themes.

## Motion

Feedback colors use an interruptible 140 ms Compose tween. Clicks are not delayed. Shared actions
use one animated container with their Material ripple disabled locally, so two hover layers do not
compete. Transparent state colors retain their tint and animate only opacity; rows use a subtle
1.2% neutral layer with a persistent outline that strengthens on hover. Notes have 8 dp inner
padding and date overlays clip feedback to the same rounded shape as the control.
Group/weekday/visit chips use one flat container and a selection border, without a separate label
background. Native menus, pickers, and dialogs retain their Material behavior. Larger modal/list
animations are deferred.
Compose animation APIs use the runtime's `MotionDurationScale`; tests exercise scale 0. This does
not establish that macOS Reduce Motion is connected to that runtime setting; verify native
preference propagation separately before claiming support.

## Verification

`SimplioContrastTest` checks text at 4.5:1 and interactive icons/focus at 3:1, including composited
selected/hover/press layers. `SimplioActionsUiTest` checks keyboard activation, disabled state,
toggle semantics, minimum size, and zero-motion operation in both themes.
`SilverAppearanceUiTest` renders real screens in both appearances and checks alignment, toolbar
order, Duo registration/calendar access, and draft attendance interaction. Set the environment
variable `SIMPLIO_VISUAL_OUTPUT` to an absolute directory to export its PNGs.
`SimplioFeedbackUiTest` samples intermediate hover frames in both themes to catch brightness
overshoot, checks rounded overlay corners, and preserves chip selection/disabled semantics.
Manage groups uses 16 dp card insets, 12 dp section gaps and 4 dp gaps between related text lines.

Native macOS fonts, system appearance changes, hover/keyboard feel, and Reduce Motion need a manual
pass on macOS. Contrast tests and rendered smoke tests do not constitute a full accessibility audit.

## References

- [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Custom design systems and Material wrappers](https://developer.android.com/develop/ui/compose/designsystems/custom)
- [Interaction sources and user feedback](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions)
- [Focus navigation in Compose](https://developer.android.com/develop/ui/compose/touch-input/focus/focus-traversal)
- [W3C focus-visible technique (web UX reference)](https://www.w3.org/WAI/WCAG22/Techniques/css/C45)
- [Accessibility defaults and target sizes](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- [Compose animation](https://developer.android.com/develop/ui/compose/animation/quick-guide)
- [Apple HIG: Color](https://developer.apple.com/design/human-interface-guidelines/color)
- [Apple HIG: Motion](https://developer.apple.com/design/human-interface-guidelines/motion)

Package-history counts use a single underline for hover or keyboard focus, without an additional
focus frame or background. Group headings have no divider; spacing separates them from the first
rounded row. Checkbox rows clip their feedback and keep one toggle target. Manage groups
reserves a scrollbar gutter, shows an always-visible thumb while the list overflows, and includes
a scrolling hint. Package numeric overflow messages name the actual maximum and remain visible
when an additional rejected digit leaves the current value out of range.
