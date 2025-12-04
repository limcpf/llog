# AGENTS.md — 퍼블리싱 프로젝트 작업 규칙

본 문서는 저장소 루트에 위치하며, 하위 모든 디렉토리에 적용됩니다. 이 규칙은 HTML/CSS만으로 운영되는 정적 블로그 퍼블리싱 프로젝트를 안정적·일관되게 진행하기 위한 기준입니다. 명시적으로 허용되지 않은 도구/기능은 기본적으로 사용하지 않습니다.

중요: 현재 저장소는 “제너레이터가 소스 오브 트루스”입니다. 공유 자산(CSS/폰트/partials)은 `src/main/resources/templates/`에 보관되며, 릴리스 산출물은 제너레이터 CLI를 통해 생성합니다. 루트에 별도의 데모 HTML을 유지하지 않습니다.

## 1) 프로젝트 목표와 범위
- 범위: 정적 HTML + CSS 템플릿과 이를 빌드하는 Java CLI(본 레포).
- 주의: 본 레포는 제너레이터이므로 빌드 도구(Gradle/GraalVM)를 사용합니다. 템플릿을 소비하는 별도 콘텐츠 레포에서는 JS/빌드 도구 사용을 지양합니다.
- 예외: 접근성 향상 또는 필수 기능(예: 스킵링크 폴리필) 등 최소한의 JS가 반드시 필요할 경우, 사전 합의 후 `assets/js/`에 단일 파일로 추가.
- 목표: 빠른 로딩, 높은 접근성(AA 이상), 유지보수 용이성, 검색/공유에 우호적인 SEO/OG 메타 구성.

## 2) 디렉터리 구조(권장)
- 루트: `index.html`, `about.html`, `404.html` 등 페이지 파일.
- `posts/`: 블로그 글 개별 페이지. 파일명은 `YYYY-MM-DD-slug.html`.
- `assets/css/`: 스타일 자산.
  - `tokens.css` (디자인 토큰: 컬러/타이포/간격)
  - `base.css` (리셋/기본 요소 스타일)
  - `layout.css` (그리드/컨테이너/레이아웃)
  - `components.css` (버튼/카드/네비 등 컴포넌트)
  - `utilities.css` (헬퍼/유틸 클래스)
  - `print.css` (인쇄 스타일, 필요 시)
  - `index.css` (위 파일들을 `@import`로 병합하거나 링크 순서 정의)
- `assets/img/`: 이미지(최적화된 원본, webp/avif 우선, 필요 시 png/jpg 폴백)
- `assets/fonts/`: 웹폰트(가능하면 시스템 폰트 우선. 라이선스 명확 시에만 포함)
- `partials/`: 수동 복사용 공통 조각(헤더/푸터/메타 스니펫 등. 런타임 인클루드 없음)

## 3) 파일/네이밍 규칙
- 파일명/폴더명: 소문자-kebab-case. 공백/특수문자 금지. 예) `about-team.html`, `site-header.png`
- 포스트: `posts/YYYY-MM-DD-slug.html`. 슬러그는 영문-kebab-case.
- 클래스 네이밍: BEM + 접두사
  - 컴포넌트: `c-` (예: `c-card`, `c-card__title`, `c-card--featured`)
  - 레이아웃: `l-` (예: `l-container`, `l-grid`)
  - 유틸리티: `u-` (예: `u-sr-only`, `u-text-center`)
  - 상태: `is-`, `has-` (예: `is-active`, `has-error`)
- id 사용 지양(앵커/폼 라벨 연결 등 필요한 경우만). 스타일링은 클래스 기반.
- 들여쓰기 2 스페이스, 파일 마지막 개행 유지, 불필요한 트레일링 스페이스 금지.

## 4) HTML 원칙
- `<!doctype html>` + `<html lang="ko">` 필수. 문서의 주요 언어가 다를 경우 페이지 단위로 `lang` 조정.
- `<meta charset="utf-8">`, `<meta name="viewport" content="width=device-width, initial-scale=1">`, `<meta name="color-scheme" content="light dark">` 포함.
- 시맨틱 태그 사용: `header`, `nav`, `main`, `article`, `section`, `aside`, `footer`.
- 헤딩 계층 준수: 페이지당 `h1` 1개. 수준 건너뛰지 않기.
- 링크 텍스트는 의미 있게, 외부 링크는 `rel="noopener"`.
- 내비게이션 현재 페이지에 `aria-current="page"` 적용.
- 이미지: 반드시 `alt` 제공. 장식용 이미지는 `alt=""`.
- 페이지 메타: 고유 `title`, `meta name="description"`, `link rel="canonical"` 설정.

## 5) SEO/공유 메타
- Open Graph/Twitter 카드 메타를 각 페이지에 포함.
  - `og:title`, `og:description`, `og:type`, `og:url`, `og:image`(규격 1200x630 권장)
  - `twitter:card`(`summary_large_image` 권장)
- 구조화 데이터(JSON-LD)는 포스트에 `BlogPosting` 스키마 권장.
- URL은 영문-kebab-case, 일관된 디렉터리 구조 유지.

## 6) 접근성(A11y)
- 색 대비 WCAG AA 이상(일반 텍스트 4.5:1, 큰 텍스트 3:1).
- 키보드 내비게이션 가능: 포커스 순서/표시 명확, `:focus-visible` 스타일 제공.
- 스킵 링크 제공: 문서 최상단에 `a`로 `main`으로 이동.
- 폼 요소는 레이블과 연결(`label for`, `aria-labelledby`).
- ARIA는 의미 보강 용도로만 사용. 시맨틱이 우선.

## 7) CSS 아키텍처/스타일 가이드
- 아키텍처: 토큰 → 베이스(리셋) → 레이아웃 → 컴포넌트 → 유틸리티 → 오버라이드 순.
- Cascade Layers 권장: `@layer reset, base, layout, components, utilities, overrides;`
- 단위: `rem`/`em` 우선. 간격 스케일 예) 2, 4, 8, 12, 16, 24, 32, 48(px 환산).
- 디자인 토큰: `:root`에 CSS 변수 정의(색, 간격, 폰트 크기, 라운드, 섀도 등).
- 타이포: 시스템 폰트 스택 기본. 웹폰트는 `font-display: swap`과 preload 사용.
- 네이밍: BEM 일관성 유지. 컴포넌트는 독립적이고 재사용 가능하게.
- 반응형: 모바일 퍼스트. 브레이크포인트(권장) `sm: 480px`, `md: 768px`, `lg: 1024px`, `xl: 1280px`.
- 다크 모드(선택): `@media (prefers-color-scheme: dark)` 또는 `[data-theme="dark"]`로 토큰 전환.
- 리셋: 현대적 리셋 최소화 사용(예: `*, *::before, *::after { box-sizing: border-box }`, margin 제거 등), 요소 기본 의미 손상 금지.

## 8) 성능/이미지 최적화
- 외부 의존성 최소화(폰트/CDN 등). 가능하면 자체 호스팅 또는 시스템 폰트.
- 이미지: `loading="lazy"`, `decoding="async"`, 고정된 `width`/`height`로 CLS 방지.
- 반응형 이미지: `srcset`/`sizes` 제공. AVIF/WebP 우선, 폴백 제공.
- CSS는 필요한 범위로만. 크리티컬 CSS 인라인은 7KB 이내 권장(옵션).
- 파비콘/매니페스트 제공: 다양한 사이즈 아이콘, `theme-color` 메타 설정.

## 9) 페이지 생성 절차(체크리스트)
1. `partials/`의 스켈레톤을 복사해 새 HTML 생성(또는 기존 페이지에서 복사).
2. `<title>`, `meta description`, `canonical`, OG/Twitter 메타 갱신.
3. `h1`과 헤딩 구조 점검. 스킵 링크/메인 landmark 존재 확인.
4. 이미지 `alt`/크기/포맷/지연로딩 확인.
5. 내비 현재 페이지 표시(`aria-current="page"`).
6. Lighthouse(또는 대체 지표)로 성능/접근성/SEO 수동 점검.
7. HTML 유효성 검사(W3C Validator) 통과 확인.

## 10) 브라우저 지원(권장)
- 최신 에버그린 브라우저 2버전, iOS Safari 15+.
- 폴리필/JS는 원칙적으로 미사용. 필요한 경우 최소 범위로 사전 합의.

## 11) 금지/유의 사항
- JavaScript 전면 금지(사전 합의 된 최소 스크립트 제외).
- CSS 프레임워크(예: Bootstrap/Tailwind) 도입 금지. 유틸 클래스는 필요 최소한으로 직접 정의.
- 빌드 도구(webpack/vite/postcss 등) 도입 금지. 순수 정적 자산만 사용.
- 트래킹/분석 스크립트 기본 금지. 꼭 필요 시 명시적 동의 후 추가.

### 11.1 예외 — 경량 템플릿 스크립트 허용
- 목적: 도메인/사이트명 등 메타를 일괄 갱신하기 위한 최소 스크립트.
- 위치: `scripts/build.py` (Python 3, 외부 의존성 없음)
- 입력: 루트 `site.json` (예: `{"domain":"https://example.com","site_name":"사이트"}`)
- 기능: canonical/OG/RSS 링크와 feed/sitemap/robots의 도메인 문자열을 치환. HTML 구조/스타일에는 영향을 주지 않음.
- 사용: `python3 scripts/build.py`
- 금지: 번들/트랜스파일/포스트프로세싱 등 빌드 파이프라인화 금지.

## 12) 문서/변경 관리
- 본 문서(AGENTS.md)가 우선 규칙입니다. 변경 필요 시 PR 성격의 제안 후 합의하여 수정.
- 큰 결정/예외 사항은 `DECISIONS.md`에 기록(파일이 없으면 생성하여 사용).
- 페이지/스타일 변경 시 변경 요약을 커밋 메시지에 명확히 기술(예: "style: 홈 히어로 간격 토큰 적용").

## 13) 샘플 스니펫

### 13.1 HTML head 스니펫(요지)
```html
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <meta name="color-scheme" content="light dark" />
  <title>페이지 제목 — 사이트명</title>
  <meta name="description" content="이 페이지를 요약하는 한두 문장." />
  <link rel="canonical" href="https://example.com/this-page" />
  <meta property="og:title" content="페이지 제목 — 사이트명" />
  <meta property="og:description" content="요약" />
  <meta property="og:type" content="website" />
  <meta property="og:url" content="https://example.com/this-page" />
  <meta property="og:image" content="https://example.com/og/this-page.jpg" />
  <meta name="twitter:card" content="summary_large_image" />
  <link rel="stylesheet" href="/assets/css/index.css" />
</head>
```

### 13.2 스킵 링크/메인 구조
```html
<a class="u-sr-only u-sr-only--focusable" href="#main">본문 바로가기</a>
<header class="l-container" role="banner">…</header>
<nav class="l-container" aria-label="주요">…</nav>
<main id="main" class="l-container" role="main">…</main>
<footer class="l-container" role="contentinfo">…</footer>
```

### 13.3 토큰 예시
```css
:root {
  /* 색 */
  --color-bg: #ffffff;
  --color-fg: #111418;
  --color-accent: #2563eb;
  --color-muted: #6b7280;

  /* 간격(4px 계열) */
  --space-1: 0.25rem; /* 4px */
  --space-2: 0.5rem;  /* 8px */
  --space-3: 0.75rem; /* 12px */
  --space-4: 1rem;    /* 16px */
  --space-6: 1.5rem;  /* 24px */
  --space-8: 2rem;    /* 32px */

  /* 타이포 */
  --font-sans: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Noto Sans KR", "Apple SD Gothic Neo", Arial, sans-serif;
  --fs-1: clamp(0.875rem, 1.5vw, 0.95rem);
  --fs-2: clamp(1rem, 1.6vw, 1.125rem);
  --fs-3: clamp(1.25rem, 2vw, 1.5rem);
}
```

---
본 문서를 준수하면, JS/빌드 없이도 빠르고 접근 가능한 정적 블로그를 일관된 구조로 유지할 수 있습니다. 새로운 필요사항이 생기면 본 문서를 업데이트하고, 예외는 최소화합니다.

## 14) Generator 안내(평탄화)
- 경로: `src/`(코드, 리소스), `scripts/`(빌드 스크립트)
- 목적: 본 저장소의 정적 템플릿을 CLI로 초기화/빌드(GraalVM Native 포함)
- 예외: 빌드 도구/네이티브 이미지 사용 허용(본 레포는 제너레이터 자체)
