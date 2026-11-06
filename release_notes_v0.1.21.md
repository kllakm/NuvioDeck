## NuvioDeck v0.1.21 — Controller Toolbar Cleanup, OSD Badge Realignment & Upstream 0.1.35 Sync

> [!WARNING]
> **Known Issue (On-Screen Virtual Keyboard):** The Windows on-screen virtual keyboard (`TabTip.exe`) controller focus integration is still buggy and not yet resolved.

### Highlights

#### 1. Controller Toolbar Cleansing: Removed Redundant "A" & "D-Pad" Hints (`ControllerHintToolbar.kt`, `controls.js`, `PinEntryDialog.kt`)
- **Eliminated Obvious "A" Hints:** Removed `XboxGlyph.A` ("Select", "Play", "Enter Digit", "Switch Tab") from toolbar overlays across Home, Search, Library, Settings, Catalog, Details, Streams, Player, Profile Selection, and Modals, since pressing `A` to accept or activate focused items is intuitive and standard.
- **Removed D-Pad "Navigate" / "Show Controls":** Removed directional navigation hints from both the Compose hint toolbar and the HTML player OSD toolbar (`controls.js`), keeping the toolbar streamlined and reserving screen space for helpful secondary actions and shortcuts.
- **Fail-Safe Toolbar Filtering:** Added strict filtering (`it.glyph != XboxGlyph.A && it.glyph != XboxGlyph.DPad`) in both the Compose toolbar and the player OSD so `A` and `D-Pad` never populate the hint toolbar.

#### 2. Player OSD Button Letter Optical Centering & Clearance (`controls.css`, `controls.js`)
- **Geometrically Centered Face Button Letters (A, X, Y):** Replaced inverted bottom padding on `.xbox-glyph-a`, `.xbox-glyph-b`, `.xbox-glyph-x`, and `.xbox-glyph-y` with optical top padding (`padding-top: 1px; padding-bottom: 0;`), properly centering capital letters inside circular outlines without touching the top edge.
- **Lifted OSD Toolbar Above Seek Bar:** Updated the lifted player toolbar position to `bottom: max(124px, calc(var(--slider-bottom) + 96px));`, completely clearing the playback timeline scrub bar, time indicators, and focus rings so the hint bar floats gracefully above controls without overlap.

#### 3. Upstream 0.1.35 Sync (118 Commits Integrated)
- **Full Tamil Localization Suite:** Comprehensive Tamil translation (`values-ta`) across UI, metadata, player toasts, and servers, plus translation updates for Bengali, Vietnamese, and French.
- **Simkl & MDBList Ratings Tracking:** Rating source selector, star ratings on details/episodes, and two-way sync with Simkl & MDBList user ratings.
- **Playback Stall Resume & Player Reliability:** Automated closed torrent stall resumption at the exact frame (`PlaybackStallResume`), libmpv error surfacing for seamless engine failover, and watch party side-by-side resizing.
- **Desktop GIF Codec Crash Prevention:** Retains GIF codecs in memory during collection card displays to prevent native desktop crashes.
- **Submodule Pointer Update:** Aligned `MPVKit` pointer to commit `da4b7da25` for host audio session fixes.
