# SideCode Notes

> A fast, local Markdown notes, task checklist, and code scratchpad docked in your JetBrains IDE sidebar.

**SideCode Notes** blends the quick-capture elegance of **Apple Notes** and the color-coded card simplicity of **Google Keep** directly into PyCharm and IntelliJ-based IDEs.

## ✨ Key Features

- **Sidebar & Central Editor:** Edit notes right inside the compact tool window, or hit *Open in Editor* to write in a full-sized central tab with live, instantaneous two-way sync.
- **Keep & Apple Notes Aesthetics:** Pin critical notes to the top, tint cards with soft pastel colors, and filter instantly with `#tags`.
- **Interactive Checklists:** Insert task items directly alongside text. Click circles to check off items seamlessly.
- **Instant Code Snippet Capture:** Right-click any selection in the code editor and choose *"Add Selection to Notes"* to save a reference-linked Markdown note.
- **Rich Formatting Toolbar:** Format with Bold, Italic, Underline, Strikethrough, Highlight markers, Monospace code, custom font sizes (6–200 pt), and timestamps.
- **Multi-Word Search & Scope Filtering:** Lightning-fast search by keywords, tags, or pinned status. Easily toggle between **Project**-specific notes and **Global** notes available across all your projects.
- **Local-First & Git-Friendly:** Your notes are 100% yours. Stored locally as standard `.md` Markdown files with atomic saves and safe trash recovery. No cloud, no logins, no telemetry.

## 📁 Storage Locations

- **Project Scope:** `.idea/notes/` (or `.notes/`)
- **Global Scope:** `~/.pycharm-notes/`
- **Trash Recovery:** `.trash/` inside the corresponding directory

## 🛠️ Build & Development

Run `./gradlew.bat buildPlugin`. The current development build targets the locally installed PyCharm specified in `build.gradle.kts`.

## 🧪 Validation

`src/test/kotlin/com/pynotes/NoteRegression.kt` contains standalone persistence and conversion checks.
