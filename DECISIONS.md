# DECISIONS.md — 구조 일원화 메모

날짜: 2025-10-24

결정: 루트 사이트와 generator 템플릿의 중복 관리를 해소하기 위해, 공유 자산을 루트에서 단일 소스로 관리하고 제너레이터 빌드 시 자동 동기화하도록 구조를 정리했다.

변경 요약
- 루트가 단일 소스: `assets/css`, `assets/fonts`, `partials`, `assets/img/sample.svg` 관리 일원화
- 제너레이터 동기화: `generator/scripts/sync-templates.sh` 추가, Gradle `syncTemplates` 작업 도입 및 `processResources` 의존 연결
- 서비스 정렬: 제너레이터 템플릿의 폰트/서체 스택을 루트와 동일(Pretendard 우선)으로 업데이트
- 템플릿 파일 목록 자동 생성: `templates/.filelist`를 동기화 시점에 재생성

이점
- CSS/폰트/partials 편집 지점 단일화 → 드리프트 방지
- 빌드 시 자동 반영 → 수동 복사 불필요
- 기존 제너레이터 템플릿의 페이지 전용 구조(토큰 포함)는 유지하여 CLI 기능 보존

후속 과제(옵션)
- 템플릿에서 공통 head 스니펫을 include 방식으로 합치기(경량 인클루드 매크로)
- Gradle wrapper 추가 및 CI에 syncTemplates 포함
