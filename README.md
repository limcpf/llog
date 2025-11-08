# llog — 정적 블로그 템플릿 + 제너레이터(Generator as Source of Truth)

[English](README-en.md)

이 저장소는 접근성·성능·가독성에 중점을 둔 정적 블로그 템플릿과, 이를 그대로 산출하는 Java 기반 CLI 제너레이터를 함께 제공합니다. 운영은 “항상 빌드 의존”을 전제로 하며, 제너레이터 템플릿이 단일 진실의 근원(SoT)입니다.

- 템플릿/소스: `generator/src/main/resources/templates/`
- 산출물: `dist/` (배포용 정적 파일)
- 예제(데모): `examples/` (루트의 데모 HTML은 examples 아래로 이동)

## 특징
- 타이포그래피: Pretendard(가변+정적) 자체 호스팅, 한글 가독성 우선
- 접근성(A11y): 스킵 링크, 명확한 포커스, 시맨틱 구조, 대비 AA 이상 목표
- SEO/OG: canonical, og:url/image, Twitter 카드, 사이트맵/피드
- 구조/토큰: include 매크로(`<!-- @include ... -->`) + 간단 토큰(`{{KEY}}`) 치환
- 퍼포먼스: 불필요 산출 제거(문서/partials), 이미지/지연로딩 기본

## 빠른 시작(로컬)
사전 준비: Java 21 (JVM 실행), 네이티브 빌드는 GraalVM + native-image 필요(선택). 이 저장소의 메인은 generator입니다.

1) 제너레이터 실행(래퍼 스크립트)
```
bash scripts/llog --help
```

2) 샘플 사이트 생성(샘플 MD → import → dist 빌드)
```
bash scripts/llog sample --out sample-site --build
python3 -m http.server -d sample-site/dist 8080
# http://localhost:8080
```

3) 임의 워킹 디렉토리 빌드(템플릿에서 시작)
- 릴리스의 `site-skeleton.tar.gz`를 받아 워킹 디렉토리를 구성한 뒤 빌드합니다.
```
# skeleton 압축 해제 (예: ./work)
mkdir -p work && tar -xzf site-skeleton.tar.gz -C work
# 콘텐츠 주입(Markdown)
./llog import:md --src /path/to/vault --root work
# 빌드
./llog build --src work --out dist --verbose
```

## CLI 명령
```
llog 0.2.1
Usage:
  init <dir> [--dry-run] [--verbose]
  build [--src dir] [--out dir] [--dry-run] [--verbose]
  new:post --title "..." [--date YYYY-MM-DD] [--slug slug] [--root dir] [--dry-run] [--verbose]
  import:md --src <md_dir> [--root dir] [--dry-run] [--verbose]
  sample [--out dir] [--build] [--dry-run] [--verbose]
  --help | --version
```
- `sample`: 템플릿 + 샘플 MD 두 개를 포함한 예제 사이트를 생성하고, `--build` 시 dist까지 만듭니다.
- `import:md`: Front Matter에서 `title`, `createdDate(YYYY-MM-DD)`, `publish: true`를 읽어 포스트로 변환합니다.
- `build`: include → 토큰 → 도메인 순으로 전개 후 목록/태그/피드/사이트맵을 생성합니다.

## 템플릿/토큰/인클루드
- 단일 소스(SoT): `generator/src/main/resources/templates/`
- 공통 head: `partials/head-shared.html`를 페이지 `<head>`에 포함
  - 문법: `<!-- @include partials/head-shared.html -->`
  - 빌드 시 `src` 내 파일을 먼저 찾고, 없으면 패키징 템플릿 리소스(`/templates/...`)에서 폴백
- 페이지 토큰: `{{SITE_NAME}}`, `{{DOMAIN}}`, `{{OG_DEFAULT}}`, `{{YEAR}}` 등
  - 전역 토큰은 `site.json`에서 읽습니다.
- 페이지 메타(.meta.json): `<파일>.meta.json`의 값이 토큰으로 주입됩니다.
  - 예: `{ "PAGE_DESCRIPTION": "요약", "OG_IMAGE": "/og/custom.jpg" }`

### 헤더/푸터 커스텀 방법
- 공통 헤더/푸터는 Partial로 분리되어 있습니다.
  - 헤더: `generator/src/main/resources/templates/partials/site-header.html`
  - 푸터: `generator/src/main/resources/templates/partials/site-footer.html`
- 프로젝트별 커스텀은 루트의 `partials/`에 동일 경로/이름으로 파일을 두면 우선 사용됩니다.
  - 예: `partials/site-header.html`를 만들면 템플릿의 헤더를 대체합니다.
- 내비게이션 라벨은 `site.json`에서 변경:
  - `nav_home_label`, `nav_about_label`, `nav_posts_label`
- 현재 페이지 강조(aria-current)는 빌드가 자동으로 설정합니다.
  - 토큰: `{{HOME_CURRENT_ATTR}}`, `{{ABOUT_CURRENT_ATTR}}`, `{{POSTS_CURRENT_ATTR}}`
- 홈(메인) 구성 라벨/개수 커스텀은 `site.json`의 Extras로 제어:
  - `home_latest_heading`, `home_recent_heading`, `home_more_label`, `home_recent_limit`

## Pretendard(폰트)
- 자체 호스팅 WOFF2 포함(템플릿에 포함됨): Variable(100–900), Regular(400), SemiBold(600), Bold(700)
- 선언은 `assets/css/fonts.css`에서 @font-face로 정의, `--font-sans`가 최우선으로 Pretendard를 사용합니다.

## 리포지토리 구조
- `generator/` — 제너레이터 코드/템플릿/스크립트(소스 오브 트루스)
  - `src/main/resources/templates/` — 실제 템플릿(assets/partials/페이지)
  - `scripts/` — 빌드/네이티브/유틸 스크립트
- `examples/` — 예제/워크플로 템플릿(운영 대상 아님)
- `dist/` — 빌드 산출(배포용). 문서/partials/설정은 제외됨.

## 배포(릴리스)와 콘텐츠 리포 구성
- 템플릿 리포(본 리포)
  - GitHub Actions: `.github/workflows/release.yml`
  - 태그 푸시(`v*`) 시 네이티브 바이너리(각 OS) + `site-skeleton.tar.gz` + 체크섬 업로드
- 콘텐츠 리포(별도)
  - 예시 워크플로: `examples/workflows/content-build-and-deploy.yml`
  - 동작: 릴리스 자산 다운로드 → Vault(Markdown) 체크아웃 → `import:md` → `build` → Pages 배포
  - 비공개 Vault 접근 토큰은 `secrets.VAULT_TOKEN` 사용

## A11y/SEO 요약(운영 가이드)
- 스킵 링크 제공: `<a class="u-sr-only u-sr-only--focusable" href="#main">본문 바로가기</a>`
- 헤딩 계층 준수, 의미있는 링크 텍스트, `aria-current="page"` 활용
- 메타: `<title>`, `meta description`, `canonical`, Open Graph/Twitter 카드 포함
- 이미지: `alt` 필수, `width/height` 지정, `loading="lazy"`/`decoding="async"`

## 개발 팁
- 루트의 `examples/`는 참고용 데모입니다. 운영/배포는 skeleton 기반 워킹 디렉토리를 대상으로 하세요.
- dist는 배포 최소셋만 포함(문서/partials 제외). 필요한 정적 자산은 템플릿 내 `assets/`에 추가하세요.
- 네이티브 바이너리 사용 시 런타임 의존성 0으로 CI/CD를 단순화할 수 있습니다.

---
피드백/개선 제안은 이슈로 남겨주세요. 릴리스 태그 고정 사용을 권장합니다(템플릿·바이너리 버전 일치).
