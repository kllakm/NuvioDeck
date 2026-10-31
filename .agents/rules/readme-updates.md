---
description: Mandatory repository rules for controller isolation, README maintenance, and Windows build compilation
globs: "*"
---
Always follow these mandatory repository rules for every change and update:
1. **Isolate Custom Controller Code:** Keep all controller polling, input mapping, focus navigation helpers, cursor-hiding logic, and the Controller Hint Toolbar UI inside a dedicated, self-contained package/directory (e.g., `controller/`), and ensure any edits made to existing NuvioDesktop files are reduced to minimal 1-line hook-ins or wrapper calls (such as initializing the controller manager and mounting the hint toolbar overlay at the root level), reverting any unnecessary formatting or structural changes in upstream files so upstream merges stay conflict-free.
2. **Maintain `README.md`:** Document any new or modified features in the main sections and append a detailed timestamped entry to a `## Changelog / Update Log` section at the very bottom of `README.md` describing what updates were made and when.
3. **Auto-Compile Windows Build:** Always compile the standalone Windows-only executable at the end of every task before finishing.
