# AGENTS.md — Blog Generator (Java 21 + GraalVM Native) 규칙

본 문서는 `generator/` 디렉터리 이하에만 적용됩니다. 목표는 현재 저장소의 정적 템플릿(루트 HTML/CSS)을 그대로 산출하는 CLI 우선의 블로그 생성기를 Java로 구현하고, GraalVM Native Image로 의존성 없이 실행 가능한 단일 바이너리를 제공하는 것입니다.

## 1) 목표/범위
- 산출물: 현재 템플릿(루트에 있는 HTML/CSS/자산)과 동일한 구조의 정적 사이트.
- 방식: CLI 명령으로 초기화(init), 새 글(new), 빌드(build) 수행.
- 배포: 실행 파일(네이티브)만으로 사용 가능. 배포 자동화는 후속 과제.
- 확장: 테마/토큰/템플릿 교체 가능하도록 구조 확장 여지 확보.

## 2) 기술 스택
- 언어: Java 21 (switch pattern, records 사용 허용)
- 빌드: Gradle(Kotlin DSL) + `org.graalvm.buildtools.native` 플러그인
- 타겟: GraalVM Native Image, `--no-fallback` 기본
- CLI: 기본 파서는 표준 Java로 구현. 필요 시 `picocli` 1개 의존성만 허용(네이티브 친화/반사 구성 최소). 최우선은 “런타임 의존성 0”.
- JSON: 간단한 `site.json`(key→string)만 다루며, 최초 단계는 내부 경량 파서로 처리(추후 Jackson 도입 시 네이티브 리플렉션 설정 필요).

## 3) 아키텍처(패키지)
- `app` — 엔트리/CLI 파서/명령 라우팅
- `core` — 도메인 모델( `SiteConfig`, `Post`, `Paths`, `TemplateVars` )
- `service` — 유스케이스( `InitService`, `NewPostService`, `BuildService` )
- `template` — 템플릿 로더/바인더(토큰 대치, 리소스 복제)
- `infra` — 파일시스템/IO/시간/슬러그/로그
- `util` — 공용 유틸(에러/결과/함수형 헬퍼)

원칙: app→service→(core,template,infra)의 단방향 의존. 비즈니스 로직은 service/core에 집중.

## 4) CLI 명령/플래그(초기)
- `init <dir>`: 템플릿 초기화(루트 템플릿을 대상 디렉터리로 복사, `site.json` 생성)
- `new:post --title "…" [--date YYYY-MM-DD] [--slug …]`: 게시글 스캐폴딩 생성
- `build [--src .] [--out dist]`: 변수 치환/색인 생성(목록·아카이브·태그 간단 생성), 정적 산출
- `sample [--out dir] [--build]`: 템플릿 + 샘플 MD(2개)를 포함한 예제 사이트 생성. `--build`를 주면 즉시 `dist/`까지 생성.
- 공통: `--verbose`, `--dry-run`, `--version`, `--help`
  - `--dry-run`: 파일 쓰기/삭제를 수행하지 않고 예정 작업만 로깅
  - `--verbose`: 디버그 수준 로깅 출력

### 4.1 Markdown 임포트
- `import:md --src <md_dir> [--root <site_root>] [--dry-run] [--verbose]`
- 동작: `<md_dir>`의 모든 `*.md` 파일을 재귀적으로 스캔 → Front Matter(맨 위 `---`…`---` yml)에서 `title`, `createdDate(YYYY-MM-DD)`, `publish`만 읽음 → `publish: true`인 파일만 HTML 포스트로 변환
- 산출: `posts/YYYY-MM-DD-<slug>.html` + `.meta.json`
- 컨텐츠 변환: 경량 Markdown 파서(헤딩/단락/링크/이미지/코드블록/강조) → `posts/post-md-template.html`의 `{{CONTENT_HTML}}`에 주입
- 슬러그: 파일명→영문 슬러그, 불가 시 제목→영문 슬러그, 그래도 불가 시 기초 정제

## 5) 템플릿/토큰
- 템플릿 자원: `generator/templates/`에 포함(기본값은 현 루트 템플릿을 미러). 빌드 시 리소스로 패키징.
- 토큰 포맷: `{{SITE_NAME}}`, `{{DOMAIN}}`, `{{OG_DEFAULT}}`, `{{YEAR}}` 등 단순 치환.
- 토큰 소스: `site.json` + 커맨드라인 플래그. JSON은 flat key-value로 유지.
- 확장: `themes/<name>/` 디렉터리 지원 예정(후속 과제), 토큰/자산 오버레이 병합 규칙 명시 예정.

### 5.1 페이지 단위 토큰(.meta.json)
- 위치/이름: 임의의 텍스트 파일 옆에 `<filename>.meta.json`(예: `posts/2025-10-22-x.html.meta.json`).
- 형식: flat JSON(키/값 문자열). 예) `{ "PAGE_DESCRIPTION": "요약", "OG_IMAGE": "/og/custom.jpg" }`
- 치환 규칙: 글로벌 토큰 위에 오버라이드(빈 문자열은 무시). 키는 대문자로 변환되어 `{{KEY}}`로 사용.
- 내장 페이지 토큰:
  - `{{PAGE_PATH}}`: 사이트 루트 기준 경로(`/posts/..html`)
  - `{{PAGE_URL}}`: `{{DOMAIN}} + {{PAGE_PATH}}`
  - `{{OG_IMAGE}}`: 기본값은 `{{DOMAIN}}{{OG_DEFAULT}}`, 페이지 메타에서 덮어쓰기 가능
- 산출물 정리: 빌드 완료 후 `.meta.json`은 출력물에서 제거.

### 5.2 카탈로그/태그/피드 확장(M5)
- 글 스캔: `posts/YYYY-MM-DD-slug.html` 패턴 + `.meta.json`의 `PAGE_DESCRIPTION`, `TAGS`(쉼표 구분)
- 포스트 목록
  - posts/index.html: `posts_index_limit`(정수)로 최신 N개만 표시, 더보기 링크 텍스트는 `archive_more_label`(기본: "더 보기: 아카이브")
  - archives.html: 전체 연도별 목록
- 태그
  - `tag_labels`: `slug:표시명, another:Label` 형태의 매핑 문자열로 표시명을 지정
  - `untagged_label`: 태그 없는 글의 표시명(기본: "미지정")
- 피드/사이트맵: 최신 글 목록을 기반으로 자동 생성

### 5.3 페이지네이션
- 포스트: `/posts/`(1페이지), `/posts/page/2/`, `/posts/page/3/` …
- 아카이브: `/archives.html`(1페이지), `/archives/page/2/` …
- 태그: `/tags/<slug>.html`(1페이지), `/tags/<slug>/page/2/` …
- 페이지 크기: `posts_page_size`(기본 10). `archives_page_size`, `tags_page_size`로 개별 설정 가능(없으면 posts_page_size 상속). 0 또는 음수면 페이징 없이 전체 노출.
- 내비 라벨: `pagination_prev_label`(기본 “이전”), `pagination_next_label`(기본 “다음”)
- 페이지별 내용: 각 페이지에 해당하는 범위의 글을 연도 섹션으로 묶어 렌더링

## 6) 네이티브 빌드 규칙
- 동적 리플렉션/프록시/클래스패스 스캔 지양. 반드시 필요 시 GraalVM `reflect-config.json` 명시.
- 파일 IO는 `java.nio.file` 우선. 시간은 `java.time` 사용.
- 빌드 커맨드: `./gradlew nativeCompile` → `build/native/nativeCompile/llog`
- 런타임 인자 파서: 표준 구현 우선. picocli 도입 시 버전 고정과 GraalVM 설정 파일 포함.

## 7) 코딩 규칙
- 네이밍: 패키지 `io.site.bloggen`. 클래스/메서드 직관적 이름 사용, 약어 지양.
- OOP: 불변 도메인(`record`), 의존 주입은 수동(생성자)으로 간단히.
- 예외: 체크예외 최소화, `Result<T>` 또는 `Either` 유사 패턴으로 에러 메시지/코드 반환.
- 로깅: 표준 출력(정보), 표준 에러(경고/에러). 외부 로깅 프레임워크 불사용.
- 테스트: JUnit 5(단위 위주). 네이티브 테스트는 후속.

### 7.1 종료 코드/에러 정책
- 종료 코드: OK=0, UNKNOWN=1, USAGE=2, IO=3, CONFIG=4
- 모든 서비스는 `Result<T>`로 성공/실패를 반환, 메시지는 사용자 친화적으로 작성
- CLI는 Result.Err의 code로 `System.exit(code)` 호출

## 8) 성능/UX
- 시작시간/바이너리 크기 최적화: 불필요 의존성 금지, 리플렉션 피하기.
- 출력은 한국어/영어 혼용 허용. CLI 도움말은 간결/예시 포함.
- 실패 시 종료 코드 엄수(0/1/2…).

## 9) 리포 구조(제너레이터)
- `generator/AGENTS.md`(본 문서)
- `generator/TASKS.md`(활동 체크리스트)
- `generator/src/main/resources/templates/`(정적 템플릿; 루트의 공유 자산과 자동 동기화됨)
- `generator/app|core|service|template|infra|util`(소스 디렉터리)
- `generator/build.gradle.kts`, `settings.gradle.kts`

## 10) 동기화 정책(일원화)
- 단일 소스: 루트 `assets/css`, `assets/fonts`, `partials`, `assets/img/sample.svg`가 진실의 근원입니다.
- 자동 동기화: 빌드 전에 `generator/scripts/sync-templates.sh`가 실행되어 위 디렉터리를 `src/main/resources/templates/`로 복사하고, `.filelist`를 재생성합니다.
- Gradle 연계: `processResources`가 `syncTemplates` 작업에 의존합니다(Gradle 사용 시 자동 실행).
- 주의: `templates/` 내의 페이지 템플릿(`posts/index.html`, `archives.html`, `tags/*`, `posts/post-*.html`)은 제너레이터 전용(토큰 포함)이므로 여기서 관리합니다. 공유 자산(CSS/폰트/partials)은 루트에서만 수정하세요.

## 11) 문서/워크플로우
- 작업 전 `generator/TASKS.md` 업데이트(선행/병행/완료 체크).
- 커밋 메시지 접두어: `gen:`(생성기 코드), `tmpl:`(템플릿 복제/동기화), `docs:`(문서).
- 릴리스 아티팩트: `llog` 네이티브 바이너리.

## 12) 참고
- 진행 상태/우선순위: `generator/TASKS.md` 참조.
- 루트 퍼블리싱 규칙은 `generator/` 바깥에만 적용.
