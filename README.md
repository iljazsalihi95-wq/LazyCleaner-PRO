# LazyCleaner PRO
Native Android Kotlin starter. Scans a user-selected folder via Storage Access Framework and identifies byte-identical files with SHA-256. **No deletion is implemented.**

## Build
GitHub Actions > Build Android APK > Run workflow. Download the debug APK artifact after a successful run.

## Status
Implemented: directory picker, recursive local scan, HTML and screenshot counters, exact duplicate hashes, Albanian dark UI.
Not implemented: email integration, AI API connection, backup, uninstall suggestions, deletion, similarity detection. Do not put OpenAI API keys or tokens in this public repository or APK.
Backend endpoint must use authenticated access; do not expose a privileged Apps Script endpoint publicly.
