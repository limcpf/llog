Place self-hosted webfonts here (WOFF2). Licenses must allow redistribution.

Pretendard (recommended, Korean-first)
- Option A — Variable: PretendardVariable.woff2 (wght 100–900)
- Option B — Static: Pretendard-Regular.woff2 (400), Pretendard-Bold.woff2 (700)

Notes
- The CSS stack already prefers "Pretendard Variable", Pretendard; if the
  files are present locally or installed on the system, they’ll be used.
- To self-host, add the files above, then (optionally) add @font-face with URL
  sources in assets/css/fonts.css. Keep font-display: swap and Korean unicode
  ranges when possible.

Legacy (optional serif for headings)
- NotoSerifKR-Regular.woff2 (400)
- NotoSerifKR-SemiBold.woff2 (600)

These are only used if you explicitly switch components to the serif stack.
