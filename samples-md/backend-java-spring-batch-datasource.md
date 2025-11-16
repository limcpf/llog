---
title: "스프링 배치 DataSource 구성"
createdDate: 2025-07-23
publish: true
category_path: backend/java/spring-batch
tags: ["spring", "batch", "jdbc"]
---

이 문서는 스프링 배치 환경에서 `DataSource`를 설정하는 기본 원칙을 정리합니다.

## 핵심

| 사용 위치    | 역할                         |
| ------------- | ---------------------------- |
| JobRepository | JobInstance, JobExecution 저장 |
| JobExplorer   | 실행 기록 조회                 |
| JobLauncher   | Job 실행 시 메타정보 저장       |
| StepExecution | Step의 상태 저장               |

---

> 배치 메타 데이터는 일관성 있는 트랜잭션 경계로 관리하세요.

### 예시 코드(Java)

```java
@Configuration
public class BatchDataSourceConfig {
  @Bean
  public DataSource dataSource() {
    HikariConfig cfg = new HikariConfig();
    cfg.setJdbcUrl("jdbc:postgresql://localhost:5432/app");
    cfg.setUsername("app");
    cfg.setPassword("secret");
    return new HikariDataSource(cfg);
  }
}
```

필요에 따라 읽기/쓰기 분리와 커넥션 풀 파라미터를 조정합니다.

