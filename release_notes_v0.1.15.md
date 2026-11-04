### NuvioDeck v0.1.15 Release Notes

#### Highlights & Bug Fixes

- **Resolved: Player OSD Stream Sources Navigation:**
  - Fixed the issue where navigating stream sources on the video player OSD caused rapid flashing between "Reload" and the top stream.
  - Eliminated controller command token timing race conditions in `controls.js`, preventing destructive modal DOM re-renders and virtualizer wipes during active navigation.
  - Added focus retention to `renderSourceVirtualRows` so focused stream rows retain active DOM focus and focus halo styles without reverting to `document.body` or `#sourceReloadButton`.
  - Implemented full D-Pad traversal for movie sources (`sourcesModal`) and TV episode streams (`episodesModal`), providing seamless navigation between the Reload/Close header buttons, filter chips, and stream cards.

- **First-Movement Controller Wake-Up UX:**
  - When the app is dormant in desktop/mouse mode, moving the controller D-Pad or analog stick now wakes the app into controller mode and illuminates the default focus (Profile 0 on the login screen) without immediately jumping to the next profile slot. Subsequent inputs navigate normally once active.

- **Login Screen Refinements & 700ms Selection Fade-Out:**
  - Added mouse hover selection background fill to profile avatar cards matching the controller selection highlight.
  - Added smooth 700ms animated fade-out transitions (`tween(700, easing = FastOutSlowInEasing)`) for background alpha and scale whenever a profile card is unhovered or controller focus shifts.
  - Relocated the NuvioDeck logo to the top-left corner with increased, comfortable sizing (30.dp tablet/desktop, 26.dp compact).
  - Re-engineered `NuvioDeckMonikerBadge` with an explicit, compact height constraint (52% of wordmark height) and `lineHeight = fontSize`, perfectly vertically centering the "DECK" text and border box against the "nuvio" wordmark.

- **GitHub Actions CI Sideload Source Guard:**
  - Added an automated check in `.github/workflows/update-store-source.yml` detecting when 0 iOS `.ipa` assets are attached, logging a skip notice and cleanly completing the action with success (exit 0) so Windows desktop releases maintain a pristine green Actions status.

