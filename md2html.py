#!/usr/bin/env python3
"""
md2html.py
Convert a Markdown file into a readable HTML page with:
  - Client-side Markdown rendering (marked)
  - Mermaid diagram rendering
  - Clean, accessible light/dark theme with auto preference + toggle
  - Table of Contents and deep-link helper
  - Choice of how viewer libraries (marked + mermaid) are provided:
      --libs cdn           -> load from CDN at view time (internet required to view)
      --libs inline-local  -> inline local JS copies into the HTML (no internet to view)
      --libs inline-fetch  -> fetch from CDN at conversion time and inline (no internet to view later)

Usage:
  python3 scripts/md2html.py <input.md> [output.html] [--title "Custom Title"] [--theme auto|light|dark] [--libs cdn|inline-local|inline-fetch]

Examples:
  python3 scripts/md2html.py docs/Helidon-TLS.md
  python3 scripts/md2html.py docs/Helidon-TLS.md docs/Helidon-TLS.html --title "Helidon TLS/mTLS Playbook (JDK 24)"
  python3 scripts/md2html.py docs/Helidon-TLS.md --theme dark --libs inline-local

Notes:
- No Python packages required (pure stdlib).
- For --libs inline-local, place JS files here:
    scripts/vendor/marked.min.js
    scripts/vendor/mermaid.min.js
  You can download them once with:
    curl -L -o scripts/vendor/marked.min.js https://cdn.jsdelivr.net/npm/marked/marked.min.js
    curl -L -o scripts/vendor/mermaid.min.js https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js
- --libs cdn requires internet at view time (to load the libraries in the browser).
- --libs inline-fetch requires internet at conversion time only; the resulting HTML works offline.
"""

import sys
import os
import re
from pathlib import Path
from urllib.request import urlopen

LIB_URLS = {
    "marked": "https://cdn.jsdelivr.net/npm/marked/marked.min.js",
    "mermaid": "https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js",
}

def usage_and_exit():
    print("""Usage:
  python3 scripts/md2html.py <input.md> [output.html] [--title "Custom Title"] [--theme auto|light|dark] [--libs cdn|inline-local|inline-fetch]
""")
    sys.exit(1)

def derive_title(md_text: str, fallback: str) -> str:
    m = re.search(r'^\s*#\s+(.+?)\s*$', md_text, flags=re.MULTILINE)
    if m:
        return m.group(1).strip()
    return fallback

def read_local_libs(script_dir: Path):
    vendor_dir = script_dir / "vendor"
    marked_path = vendor_dir / "marked.min.js"
    mermaid_path = vendor_dir / "mermaid.min.js"
    marked_js = None
    mermaid_js = None
    if marked_path.exists():
        marked_js = marked_path.read_text(encoding="utf-8")
    if mermaid_path.exists():
        mermaid_js = mermaid_path.read_text(encoding="utf-8")
    return marked_js, mermaid_js

def fetch_lib(url: str) -> str:
    with urlopen(url, timeout=30) as resp:
        data = resp.read()
        return data.decode("utf-8", errors="replace")

def safe_inline(js: str) -> str:
    # Prevent closing the script tag from breaking HTML parsing
    return js.replace("</script", "<\\/script")

def main():
    args = sys.argv[1:]
    if not args or "-h" in args or "--help" in args:
        usage_and_exit()

    input_path = Path(args[0]).expanduser()
    if not input_path.exists():
        print(f"Input file not found: {input_path}", file=sys.stderr)
        sys.exit(2)

    # Determine output path
    if len(args) > 1 and not args[1].startswith("--"):
        output_path = Path(args[1]).expanduser()
        opt_args = args[2:]
    else:
        output_path = input_path.with_suffix(".html")
        opt_args = args[1:]

    # Optional title
    title = None
    if "--title" in opt_args:
        idx = opt_args.index("--title")
        if idx + 1 < len(opt_args):
            title = opt_args[idx + 1]

    # Optional theme (auto|light|dark) - affects initial theme only
    theme = "auto"
    if "--theme" in opt_args:
        idx = opt_args.index("--theme")
        if idx + 1 < len(opt_args):
            theme = opt_args[idx + 1].lower()
            if theme not in ("auto", "light", "dark"):
                print("Invalid --theme value. Use: auto|light|dark", file=sys.stderr)
                sys.exit(3)

    # Libraries mode
    libs_mode = "cdn"
    if "--libs" in opt_args:
        idx = opt_args.index("--libs")
        if idx + 1 < len(opt_args):
            libs_mode = opt_args[idx + 1].lower().strip()
    if libs_mode not in ("cdn", "inline-local", "inline-fetch"):
        print("Invalid --libs value. Use: cdn|inline-local|inline-fetch", file=sys.stderr)
        sys.exit(4)

    md_raw = input_path.read_text(encoding="utf-8")
    if not title:
        title = derive_title(md_raw, input_path.stem)

    # Escape closing </script to avoid breaking the embedding
    md_escaped = md_raw.replace("</script", "<\\/script")

    # Compute relative link to raw markdown
    rel_md_href = os.path.relpath(input_path, output_path.parent).replace(os.sep, "/")

    # Prepare JS libraries according to libs_mode
    script_dir = Path(__file__).resolve().parent
    libs_block = ""
    used_mode = libs_mode

    if libs_mode == "cdn":
        libs_block = (
            f'<script src="{LIB_URLS["marked"]}"></script>\n'
            f'<script src="{LIB_URLS["mermaid"]}"></script>\n'
        )
    elif libs_mode == "inline-local":
        marked_js, mermaid_js = read_local_libs(script_dir)
        if not marked_js or not mermaid_js:
            print("Warning: Local libs not found in scripts/vendor. Falling back to CDN.", file=sys.stderr)
            libs_block = (
                f'<script src="{LIB_URLS["marked"]}"></script>\n'
                f'<script src="{LIB_URLS["mermaid"]}"></script>\n'
            )
            used_mode = "cdn"
        else:
            libs_block = (
                f"<script>\n{safe_inline(marked_js)}\n</script>\n"
                f"<script>\n{safe_inline(mermaid_js)}\n</script>\n"
            )
    else:  # inline-fetch
        try:
            marked_js = fetch_lib(LIB_URLS["marked"])
            mermaid_js = fetch_lib(LIB_URLS["mermaid"])
            libs_block = (
                f"<script>\n{safe_inline(marked_js)}\n</script>\n"
                f"<script>\n{safe_inline(mermaid_js)}\n</script>\n"
            )
        except Exception as e:
            print(f"Warning: Failed to fetch libs ({e}). Falling back to CDN.", file=sys.stderr)
            libs_block = (
                f'<script src="{LIB_URLS["marked"]}"></script>\n'
                f'<script src="{LIB_URLS["mermaid"]}"></script>\n'
            )
            used_mode = "cdn"

    # Build HTML via concatenation to avoid brace escaping issues
    head_1 = "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\" />\n  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\" />\n  <title>"
    head_2 = "</title>\n  <style>\n"
    # Accessible, balanced theme (light by default, auto, and dark). Provide a pleasant reading experience.
    css = r"""
    :root {
      /* Light theme (default) */
      --bg: #ffffff;
      --panel: #f6f8fa;
      --text: #1b1f23;
      --muted: #57606a;
      --accent: #056CF2;
      --code-bg: #f6f8fa;
      --code-border: #d0d7de;
      --link: #0969da;
      --border: #d0d7de;
      --blockquote-bg: #f0f7ff;
      --blockquote-border: #9ecbff;
    }
    @media (prefers-color-scheme: dark) {
      :root {
        --bg: #0b0e14;
        --panel: #11151c;
        --text: #e6e6e6;
        --muted: #a0a7b4;
        --accent: #4cc3ff;
        --code-bg: #0f141b;
        --code-border: #1b2330;
        --link: #7bd88f;
        --border: #202839;
        --blockquote-bg: rgba(76,195,255,0.06);
        --blockquote-border: #2b3a52;
      }
    }
    :root[data-theme='light'] {
      --bg: #ffffff;
      --panel: #f6f8fa;
      --text: #1b1f23;
      --muted: #57606a;
      --accent: #056CF2;
      --code-bg: #f6f8fa;
      --code-border: #d0d7de;
      --link: #0969da;
      --border: #d0d7de;
      --blockquote-bg: #f0f7ff;
      --blockquote-border: #9ecbff;
    }
    :root[data-theme='dark'] {
      --bg: #0b0e14;
      --panel: #11151c;
      --text: #e6e6e6;
      --muted: #a0a7b4;
      --accent: #4cc3ff;
      --code-bg: #0f141b;
      --code-border: #1b2330;
      --link: #7bd88f;
      --border: #202839;
      --blockquote-bg: rgba(76,195,255,0.06);
      --blockquote-border: #2b3a52;
    }

    html, body {
      margin: 0;
      padding: 0;
      background: var(--bg);
      color: var(--text);
      font-family: ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Ubuntu, Cantarell, "Fira Sans", "Droid Sans", "Helvetica Neue", Arial, sans-serif;
      line-height: 1.7;
    }
    main {
      max-width: 960px;
      margin: 2.2rem auto 4rem;
      padding: 0 1rem;
    }
    h1, h2, h3, h4 {
      color: var(--text);
      line-height: 1.25;
      margin-top: 1.6rem;
    }
    h1 { font-size: 2rem; margin-top: 0.5rem; }
    h2 { font-size: 1.6rem; }
    h3 { font-size: 1.3rem; }
    p, li { color: var(--text); }
    a { color: var(--link); text-decoration: none; }
    a:hover, a:focus { text-decoration: underline; }

    pre, code, kbd, samp {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
      font-size: 0.95rem;
    }
    pre {
      background: var(--code-bg);
      border: 1px solid var(--code-border);
      border-radius: 8px;
      padding: 0.9rem 1rem;
      overflow: auto;
    }
    code {
      background: rgba(127,127,127,0.12);
      border-radius: 6px;
      padding: 0.1rem 0.35rem;
    }
    pre code {
      background: transparent;
      padding: 0;
    }

    blockquote {
      margin: 1rem 0;
      padding: 0.6rem 1rem;
      border-left: 4px solid var(--blockquote-border);
      background: var(--blockquote-bg);
      color: var(--text);
    }
    hr {
      border: none;
      height: 1px;
      background: linear-gradient(to right, transparent, var(--border), transparent);
      margin: 2rem 0;
    }
    ul, ol { padding-left: 1.2rem; }
    .panel {
      background: var(--panel);
      border: 1px solid var(--border);
      border-radius: 10px;
      padding: 1rem 1.2rem;
    }

    .toc {
      margin: 1rem 0 2rem;
    }
    .toc h2 {
      margin: 0 0 0.5rem 0;
      font-size: 1.05rem;
      color: var(--muted);
    }

    .mermaid {
      background: var(--panel);
      border: 1px solid var(--border);
      border-radius: 10px;
      padding: 1rem;
      margin: 1rem 0;
      overflow-x: auto;
    }

    .top-actions {
      display: flex;
      gap: 0.5rem;
      flex-wrap: wrap;
      align-items: center;
      margin: 0 0 1rem;
    }
    .btn {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.45rem 0.75rem;
      border-radius: 8px;
      border: 1px solid var(--border);
      background: var(--panel);
      color: var(--text);
      cursor: pointer;
      text-decoration: none;
    }
    .btn:hover {
      box-shadow: 0 0 0 1px var(--border);
    }
    .small {
      font-size: 0.9rem;
      color: var(--muted);
    }
    .right { margin-left: auto; }
    """
    head_3 = "\n  </style>\n</head>\n<body>\n  <main>\n    <div class=\"top-actions\">\n      <a class=\"btn\" href=\""
    head_4 = "\" target=\"_blank\" rel=\"noopener\">View raw Markdown</a>\n"
    # Theme controls
    head_5 = "      <button class=\"btn\" id=\"themeToggle\" title=\"Toggle theme\">Theme: Auto</button>\n"
    head_6 = "      <button class=\"btn\" id=\"copyLinkBtn\">Copy link to section</button>\n"
    head_7 = "      <span class=\"small\">Markdown & diagrams render client-side.</span>\n"
    head_8 = "    </div>\n    <div class=\"toc panel\" id=\"toc\">\n      <h2>Table of Contents</h2>\n      <div id=\"toc-contents\" class=\"small\">Loading…</div>\n    </div>\n    <article id=\"content\"></article>\n\n    <!-- Embed the original Markdown safely -->\n    <script id=\"md\" type=\"text/markdown\">\n"
    tail_md_close = "\n    </script>\n  </main>\n\n  <!-- Viewer Libraries -->\n"
    # JS: marked setup, render, TOC, mermaid transform, theme toggle, copy link helper
    js = r"""
  <script>
    // Theme preference: 'auto' | 'light' | 'dark'
    (function() {
      const root = document.documentElement;
      const btn = document.getElementById('themeToggle');
      const saved = localStorage.getItem('theme-pref') || '""" + theme + r"""';
      function apply(theme) {
        if (theme === 'auto') {
          root.removeAttribute('data-theme');
          btn.textContent = 'Theme: Auto';
        } else {
          root.setAttribute('data-theme', theme);
          btn.textContent = 'Theme: ' + (theme.charAt(0).toUpperCase() + theme.slice(1));
        }
        localStorage.setItem('theme-pref', theme);
      }
      let current = saved;
      apply(current);
      btn.addEventListener('click', () => {
        current = (current === 'auto') ? 'light' : (current === 'light') ? 'dark' : 'auto';
        apply(current);
      });
    })();

    // Configure marked for client-side Markdown rendering
    marked.setOptions({
      gfm: true,
      breaks: false,
      headerIds: true,
      mangle: false
    });

    // Render Markdown into the article
    const md = document.getElementById('md').textContent;
    const content = document.getElementById('content');
    content.innerHTML = marked.parse(md);

    // Build a simple Table of Contents
    const tocContainer = document.getElementById('toc-contents');
    const headings = [...content.querySelectorAll('h1, h2, h3')];
    const toc = document.createElement('ul');
    toc.style.listStyle = 'none';
    toc.style.paddingLeft = '0';
    headings.forEach(h => {
      if (!h.id) {
        h.id = h.textContent.trim().toLowerCase().replace(/[^\w]+/g, '-');
      }
      const li = document.createElement('li');
      li.style.margin = '0.15rem 0';
      li.style.paddingLeft = (h.tagName === 'H1') ? '0' : (h.tagName === 'H2') ? '0.75rem' : '1.5rem';
      const a = document.createElement('a');
      a.href = '#' + h.id;
      a.textContent = h.textContent;
      li.appendChild(a);
      toc.appendChild(li);
    });
    tocContainer.innerHTML = '';
    tocContainer.appendChild(toc);

    // Transform fenced mermaid code blocks into <div class="mermaid">
    const mermaidCodes = content.querySelectorAll('pre > code.language-mermaid');
    mermaidCodes.forEach(code => {
      const pre = code.parentElement;
      const graph = code.textContent;
      const div = document.createElement('div');
      div.className = 'mermaid';
      div.textContent = graph;
      pre.replaceWith(div);
    });

    // Initialize Mermaid (theme adapts via page styles)
    mermaid.initialize({ startOnLoad: false, securityLevel: 'loose', theme: 'default' });
    mermaid.run({ querySelector: '.mermaid' });

    // Copy link to section helper
    document.getElementById('copyLinkBtn').addEventListener('click', () => {
      const sel = document.getSelection();
      let target = content.querySelector(':target') || headings[0];
      if (sel && sel.anchorNode) {
        const node = sel.anchorNode.nodeType === 1 ? sel.anchorNode : sel.anchorNode.parentElement;
        const all = [...headings];
        const idx = all.findIndex(h => h.compareDocumentPosition(node) & Node.DOCUMENT_POSITION_FOLLOWING);
        if (idx > 0) target = all[idx - 1];
      }
      const url = location.origin + location.pathname + '#' + (target ? target.id : '');
      navigator.clipboard.writeText(url).then(() => {
        const btn = document.getElementById('copyLinkBtn');
        const original = btn.textContent;
        btn.textContent = 'Copied!';
        setTimeout(() => btn.textContent = original, 1200);
      });
    });
  </script>
</body>
</html>
"""

    # Assemble HTML
    html_parts = [
        head_1, title, head_2, css, head_3, rel_md_href, head_4, head_5, head_6, head_7,
        head_8, md_escaped, tail_md_close, libs_block, js
    ]
    html = "".join(html_parts)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(html, encoding="utf-8")

    print("HTML generated:")
    print(f"  Input : {input_path.resolve()}")
    print(f"  Output: {output_path.resolve()}")
    print(f"  Title : {title}")
    print(f"  Theme : {theme} (initial, toggle in-page)")
    print(f"  Libs  : {libs_mode} (effective: {used_mode})")
    print(f"  Raw MD link: {rel_md_href}")

if __name__ == "__main__":
    main()
