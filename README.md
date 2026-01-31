# HARI_UTILS – Markdown Collector (collect_markdown.py)

This utility scans a source project for Markdown files and copies or moves them into a docs directory. It preserves directory structure by default and can optionally flatten all files into a single folder. A manifest CSV is written listing all transfers.

Defaults:
- Source: current working directory (or specify with `--source`)
- Destination: /Users/harikorivi/Documents/Current_tasks/OCCAS_AI_GW/docs
- Action: copy (use `--move` to move)
- Structure: preserve (use `--flatten` to place all files directly under destination)

Key options:
- `-s, --source`  Source project root
- `-d, --dest`    Destination root (default as above)
- `--move`        Move instead of copy
- `--flatten`     Do not preserve directory structure
- `--dry-run`     Preview actions without changing files
- `--include/--exclude`  Fine-tune which paths are processed

Examples:
- Copy from OCCAS project to default destination:
  python3 collect_markdown.py -s "/Users/harikorivi/CGIU_PRODUCTS/OCCAS/SRC810_OCCAS/occas"

- Move instead of copy:
  python3 collect_markdown.py -s "/path/to/project" --move

- Flatten all files into destination root:
  python3 collect_markdown.py -s "/path/to/project" --flatten

- Dry run (no changes):
  python3 collect_markdown.py -s "/path/to/project" --dry-run

Notes:
- A manifest file `collected_markdown_manifest.csv` is written in the destination.
- Hidden and heavy directories (e.g., .git, target, node_modules) are skipped by default.
- Use `--include-hidden` to include hidden directories/files.
