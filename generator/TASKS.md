# TASKS — Blog Generator Roadmap

상태 표기: [x] 완료 · [ ] 진행 예정 · [~] 진행 중

## M0 — 문서/스펙
- [x] AGENTS(규칙) 수립: generator/AGENTS.md
- [x] 태스크 보드 생성: generator/TASKS.md
- [x] 루트 AGENTS에 서브프로젝트 범위 명시

## M1 — 프로젝트 스캐폴딩
- [x] Gradle Kotlin DSL 초기화(자바 21 toolchain)
- [x] GraalVM Native 플러그인 설정(`org.graalvm.buildtools.native`)
- [x] 모듈 패키지 생성(app, core, service, template, infra, util)
- [x] 기본 엔트리포인트(`main(String[] args)`)와 버전/헬프 출력

## M2 — 템플릿 리소스/치환
- [x] `generator/templates/`에 루트 템플릿 복제 및 토큰 주석 표기(기본 복제 완료)
- [ ] 간단 토큰 엔진(문자열 치환) 구현
- [x] `site.json` 로더(경량 JSON 파서) 구현
- [x] `build` 커맨드: 템플릿/소스 → out 디렉터리 복사 + 도메인 치환

## M3 — CLI 명령/유스케이스
- [x] `init <dir>`: 디렉터리 생성 + 기본 `site.json` 생성
- [x] `new:post` 생성기: 날짜/슬러그 규칙 적용, 파일 스캐폴딩
- [x] `build` 옵션: `--src`, `--out`, `--dry-run`, `--verbose`
- [x] `init/new:post`에 `--dry-run`, `--verbose` 추가
- [x] 에러 코드/메시지 표준화(Result 패턴)

## M4 — 네이티브 빌드/테스트
- [ ] `./gradlew nativeCompile`로 네이티브 실행 파일 생성
- [ ] 기본 시나리오 테스트(초기화→글 생성→빌드)
- [ ] 크기/시작시간 점검, 리플렉션 금지 준수 확인

## M5 — 카탈로그/색인(선택)
- [x] 글 목록/아카이브/태그 페이지 간단 생성기
- [x] feed.xml/sitemap.xml 템플릿화 (robots.txt는 도메인 토큰만 유지)

## M6 — 확장 여지
- [ ] 테마 디렉터리/오버레이 병합 규칙 정의
- [ ] (선택) picocli 도입 시 GraalVM 설정 포함
- [ ] (선택) JSON 파서 교체(Jackson Core) + native config

## 유지보수/운영
- [ ] CHANGELOG.md 도입
- [ ] GitHub Actions(선택): 네이티브 빌드 CI
