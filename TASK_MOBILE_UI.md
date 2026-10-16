# TASK: WordCraft Mobile View-Only Mode, Page Scrubber, & Stretch Layout

## User Requirements
1. **Right-side Page Navigation Scrollbar / Scrubber**:
   - Provide a touch-friendly vertical page scrubber on the right side of the screen.
   - Shows current page / total pages (e.g. "Halaman 1 dari 5").
   - Dragging or tapping the scrubber jumps/scrolls directly to that page.
2. **Default to View-Only Mode upon Opening Document**:
   - When a document is first opened, it must NOT open in Edit mode with heavy desktop ribbons.
   - It must open in clean **View-Only Mode**:
     - Desktop ribbon, rulers, search bar, and status bar are hidden.
     - A clean mobile top bar displays the document title, a Back button to return to home, and a **Pencil / Edit button**.
     - Only when the user taps the Pencil button does it switch to Edit mode (showing ribbon/formatting tools).
     - In Edit mode, a "Selesai" / "View" checkmark button allows returning to View-Only mode.
3. **Expand / Stretch Full View Toggle (Like PdfCraft)**:
   - Provide a diagonal arrow button (`maximize-2` / `minimize-2`) in the bottom-right corner.
   - Tapping it toggles between **Single-Page Stretch / Fit-to-Width** (page stretches edge-to-edge across the screen for large, readable text) and **Whole-Page Overview** (`fit = "onePage"`).
   - In stretch mode, eliminate wide desktop side margins (gutters) so text is crystal clear and legible without microscopic 37% zoom.

---

## Detailed Implementation Plan

### 1. `crates/ui-egui/src/icons.rs`
Add `"maximize-2"` and `"minimize-2"` diagonal arrow icons on the 20x20 grid:
```rust
"maximize-2" => {
    pen.line_c(&[(13.0, 3.0), (17.0, 3.0), (17.0, 7.0)], c);
    pen.line_c(&[(17.0, 3.0), (11.0, 9.0)], c);
    pen.line_c(&[(7.0, 17.0), (3.0, 17.0), (3.0, 13.0)], c);
    pen.line_c(&[(3.0, 17.0), (9.0, 11.0)], c);
}
"minimize-2" => {
    pen.line_c(&[(17.0, 7.0), (13.0, 7.0), (13.0, 3.0)], c);
    pen.line_c(&[(13.0, 7.0), (18.0, 2.0)], c);
    pen.line_c(&[(3.0, 13.0), (7.0, 13.0), (7.0, 17.0)], c);
    pen.line_c(&[(7.0, 13.0), (2.0, 18.0)], c);
}
```

### 2. `crates/ui-egui/src/chrome.rs`
1. `pub fn is_compact_mobile(ctx: &egui::Context) -> bool`:
   ```rust
   let rect = ctx.content_rect();
   rect.width() < 680.0 || (rect.height() < 500.0 && rect.width() < 920.0)
   ```
2. `pub fn mobile_top_bar(app: &mut WordApp, ui: &mut egui::Ui)`:
   - Panel top with height 46.0, themed fill.
   - If `!app.mobile_edit_mode` (View-Only):
     - Left: Back button (`chevronLeft`, 40x40 touch target) -> `app.start_screen()`.
     - Center: Document title (truncated with ellipsis, semibold 14pt).
     - Right: Edit button with `pencil` icon and "Edit" label/tooltip. Clicking it sets `app.mobile_edit_mode = true`.
   - If `app.mobile_edit_mode` (Edit):
     - Left: Done button (`check` icon with "Selesai" or tooltip) -> sets `app.mobile_edit_mode = false`.
     - Center: Document title + " (Edit)".
     - Right: Undo (`undo`), Redo (`redo`).

3. `pub fn mobile_bottom_bar(app: &mut WordApp, ui: &mut egui::Ui)`:
   - Panel bottom with height 48.0.
   - Left:
     - Previous page button (`chevronLeft`, 36x36).
     - Page text label: `"Hal {current} / {total}"` (e.g. `1 / 2`).
     - Next page button (`chevronRight`, 36x36).
   - Right:
     - Diagonal arrow button (`if app.mobile_stretch { "minimize-2" } else { "maximize-2" }`, 36x36).
     - Tapping toggles `app.mobile_stretch = !app.mobile_stretch;`.
     - If `app.mobile_stretch`: sets `app.session.view.fit = "pageWidth".to_string();`.
     - If `!app.mobile_stretch`: sets `app.session.view.fit = "onePage".to_string();`.

4. `pub fn mobile_page_scrubber(app: &mut WordApp, ui: &mut egui::Ui)`:
   - Floating scrubber on the right edge of the screen:
     - Track rect: `x = screen_width - 32.0..screen_width - 4.0`, `y = top + 10.0..bottom - 60.0`.
     - Background capsule track.
     - Thumb pill with current page number (e.g. "1").
     - Interacting (drag or tap) computes target page from `y` fraction, and sets:
       `app.canvas.scroll_request = Some(vec2(0.0, target_y));`
     - Displays floating bubble `"Halaman X dari Y"`.

### 3. `crates/ui-egui/src/lib.rs`
1. In `WordApp`:
   - `pub mobile_edit_mode: bool`: initialized to `false`.
   - `pub mobile_stretch: bool`: initialized to `true`.
2. In `drain_inbox()` and `run("file.open", ...)`:
   - Reset `self.mobile_edit_mode = false;`
   - Reset `self.mobile_stretch = true;`
   - Set `self.session.view.fit = "pageWidth".to_string();`
3. In `ui(&mut self, ui: &mut egui::Ui)`:
   - If `chrome::is_compact_mobile(&ctx)`:
     - Show `chrome::mobile_top_bar(self, ui)`.
     - If `self.mobile_edit_mode`:
       - Show `ribbon::show(self, ui)`.
     - Else:
       - Show `chrome::mobile_bottom_bar(self, ui)`.
     - Show `egui::CentralPanel`:
       - `canvas::show(self, ui);`
       - `chrome::mobile_page_scrubber(self, ui);`
   - Else:
     - Render normal desktop UI.

### 4. `crates/ui-egui/src/canvas.rs`
1. Disable rulers on mobile:
   `let show_ruler = app.session.view.ruler && !crate::chrome::is_compact_mobile(ui.ctx()) && ...;`
2. When `crate::chrome::is_compact_mobile(ui.ctx()) && app.mobile_stretch`:
   - In scale calculation:
     `scale = (avail.x / maxw).clamp(0.1, 6.0);`
   - In `place_pages`:
     When `cols == 1` and `avail.x < 680.0`:
     Place each page at `px = (avail.x - w * scale).max(0.0) / 2.0` with minimal padding `GAP = 8.0`.
