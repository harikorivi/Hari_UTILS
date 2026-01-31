#!/usr/bin/env python3
"""
Collect Markdown files from a source project and copy or move them into a destination docs directory.

Defaults:
- Source: current working directory (or specify with --source)
- Destination: /Users/harikorivi/Documents/Current_tasks/OCCAS_AI_GW/docs
- Action: copy (use --move to move)
- Structure: preserve directory structure under destination (use --flatten to put all files directly under destination)

Features:
- Recursively scans for markdown files (.md, .markdown, .mkd, .mkdn, case-insensitive)
- Skips common heavy or irrelevant directories (configurable)
- Writes a manifest CSV at the destination with all transfers performed
- Dry run mode to preview changes

Examples:
  Copy preserving structure:
    python3 tools/scripts/collect_markdown.py

  Move preserving structure:
    python3 tools/scripts/collect_markdown.py --move

  Copy to a different destination:
    python3 tools/scripts/collect_markdown.py -d /path/to/dest

  Flatten into destination (handle name collisions automatically):
    python3 tools/scripts/collect_markdown.py --flatten

  Dry run (no changes made):
    python3 tools/scripts/collect_markdown.py --dry-run
"""

import argparse
import csv
import hashlib
import os
from pathlib import Path
import shutil
import sys
import time
from typing import Iterable, List, Set, Tuple


DEFAULT_DEST = "/Users/harikorivi/Documents/Current_tasks/OCCAS_AI_GW/OCCAS_83_docs"
DEFAULT_EXCLUDES = {
    ".git",
    ".svn",
    ".hg",
    ".idea",
    ".vscode",
    "node_modules",
    "target",
    "build",
    "dist",
    "out",
    ".tox",
    "__pycache__",
    ".venv",
    "venv",
    ".mvn",
    "coverage",
    ".DS_Store",
}
MD_EXTS = {".md", ".markdown", ".mkd", ".mkdn", ".doc", ".docx"}  # case-insensitive


def is_markdown(path: Path) -> bool:
    return path.suffix.lower() in MD_EXTS


def iter_markdown_files(source_root: Path, excludes: Set[str]) -> Iterable[Path]:
    """
    Yield absolute Paths to markdown files under source_root, skipping excluded top-level names.
    """
    for root, dirs, files in os.walk(source_root):
        # Prune excluded directories at any level
        dirs[:] = [d for d in dirs if d not in excludes and not d.startswith(".terraform")]
        for f in files:
            p = Path(root) / f
            if is_markdown(p):
                yield p


def unique_name_for_flatten(dest_dir: Path, rel_src: Path) -> str:
    """
    For flatten mode, generate a unique, readable filename in dest_dir based on the original relative path.
    Strategy:
      - base name + stem from parent parts if collision
      - fall back to appending short hash for uniqueness
    """
    base = rel_src.name
    candidate = dest_dir / base
    if not candidate.exists():
        return base

    # Try adding parent path components
    stem = rel_src.stem
    suffix = rel_src.suffix
    parts = list(rel_src.parts[:-1])  # parent parts
    parts.reverse()

    for i in range(1, min(3, len(parts)) + 1):
        decorated = f"{stem}__{'__'.join(parts[:i])}{suffix}"
        candidate = dest_dir / decorated
        if not candidate.exists():
            return decorated

    # Fall back to hash
    short_hash = hashlib.sha1(str(rel_src).encode("utf-8")).hexdigest()[:10]
    return f"{stem}__{short_hash}{suffix}"


def ensure_inside(path: Path, root: Path) -> bool:
    try:
        path.resolve().relative_to(root.resolve())
        return True
    except Exception:
        return False


def write_manifest(dest_root: Path, rows: List[Tuple[str, str, str, str, int, float]]) -> Path:
    """
    Write a manifest CSV of operations performed.
    Columns: action, src_abs, dest_abs, rel_path, size_bytes, mtime
    """
    manifest = dest_root / "collected_markdown_manifest.csv"
    first_write = not manifest.exists()
    with manifest.open("a", newline="") as f:
        w = csv.writer(f)
        if first_write:
            w.writerow(["action", "src_abs", "dest_abs", "rel_path", "size_bytes", "mtime"])
        for row in rows:
            w.writerow(row)
    return manifest


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Collect Markdown files into a docs directory.")
    parser.add_argument(
        "-s", "--source", type=str, default=None,
        help="Source project root (default: current working directory)"
    )
    parser.add_argument(
        "-d", "--dest", type=str, default=DEFAULT_DEST,
        help=f"Destination root directory (default: {DEFAULT_DEST})"
    )
    parser.add_argument(
        "--move", action="store_true",
        help="Move files instead of copying"
    )
    parser.add_argument(
        "--flatten", action="store_true",
        help="Do not preserve directory structure; place files directly under destination"
    )
    parser.add_argument(
        "--dry-run", action="store_true",
        help="Preview actions without making any changes"
    )
    parser.add_argument(
        "--include-hidden", action="store_true",
        help="Include hidden directories/files (default: hidden dirs skipped by exclude list)"
    )
    parser.add_argument(
        "--include", action="append", default=[],
        help="Additional include glob(s) to filter markdown paths (evaluated against relative paths). Can be used multiple times."
    )
    parser.add_argument(
        "--exclude", action="append", default=[],
        help="Additional directory/file name(s) to exclude (exact matches). Can be used multiple times."
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    # Determine source root
    if args.source:
        source_root = Path(args.source).resolve()
    else:
        # Default to current working directory when invoked
        source_root = Path.cwd().resolve()

    if not source_root.exists() or not source_root.is_dir():
        print(f"[ERROR] Source root does not exist or is not a directory: {source_root}", file=sys.stderr)
        return 2

    dest_root = Path(args.dest).expanduser().resolve()

    # Safety: if destination is inside source, skip it during traversal
    dest_inside_source = ensure_inside(dest_root, source_root)

    excludes = set(DEFAULT_EXCLUDES)
    excludes.update(args.exclude or [])
    if not args.include_hidden:
        excludes.update({".*"})  # implicit pattern; handled by direct name check where applicable

    print(f"[INFO] Source: {source_root}")
    print(f"[INFO] Destination: {dest_root}")
    print(f"[INFO] Mode: {'MOVE' if args.move else 'COPY'}; Structure: {'FLATTEN' if args.flatten else 'PRESERVE'}")
    print(f"[INFO] Dry run: {args.dry_run}")
    if excludes:
        print(f"[INFO] Excludes: {sorted(excludes)}")
    if args.include:
        print(f"[INFO] Include globs: {args.include}")

    # Create destination root if needed (unless dry-run)
    if not args.dry_run:
        dest_root.mkdir(parents=True, exist_ok=True)

    transferred_rows: List[Tuple[str, str, str, str, int, float]] = []
    total = 0
    copied = 0

    for src_abs in iter_markdown_files(source_root, excludes):
        # Skip if src is inside destination (when dest is under source)
        if dest_inside_source and ensure_inside(src_abs, dest_root):
            continue

        rel = src_abs.relative_to(source_root)

        # Include filters (if provided)
        if args.include:
            # Evaluate as glob patterns against stringified relative path
            rel_str = str(rel)
            if not any(Path(rel_str).match(pattern) for pattern in args.include):
                continue

        total += 1

        if args.flatten:
            out_name = unique_name_for_flatten(dest_root, rel)
            dest_abs = dest_root / out_name
            dest_rel = Path(out_name)
        else:
            dest_abs = dest_root / rel
            dest_rel = rel

        action = "MOVE" if args.move else "COPY"
        size = src_abs.stat().st_size if src_abs.exists() else 0
        mtime = src_abs.stat().st_mtime if src_abs.exists() else time.time()

        print(f"[PLAN] {action}: {src_abs} -> {dest_abs}")

        if not args.dry_run:
            dest_abs.parent.mkdir(parents=True, exist_ok=True)
            if args.move:
                # Use rename when possible, fall back to shutil.move
                try:
                    src_abs.replace(dest_abs)
                except Exception:
                    shutil.move(str(src_abs), str(dest_abs))
            else:
                # copy2 preserves metadata
                shutil.copy2(str(src_abs), str(dest_abs))

            transferred_rows.append((action, str(src_abs), str(dest_abs), str(dest_rel), size, mtime))
            copied += 1

    if not args.dry_run and transferred_rows:
        manifest = write_manifest(dest_root, transferred_rows)
        print(f"[INFO] Wrote manifest: {manifest}")

    print(f"[SUMMARY] Matched markdown files: {total}")
    if args.dry_run:
        print(f"[SUMMARY] Dry run complete. No files transferred.")
    else:
        print(f"[SUMMARY] Files {('moved' if args.move else 'copied')}: {copied}")

    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\n[ABORTED] Interrupted by user.", file=sys.stderr)
        sys.exit(130)
