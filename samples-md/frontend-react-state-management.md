---
title: "React 상태 관리 개요"
createdDate: 2025-02-11
publish: true
path: frontend/react
tags: ["react", "state", "hooks"]
---

리액트 애플리케이션에서 상태 관리는 UI 일관성과 예측 가능성을 좌우합니다. 규모/복잡도에 따라 지역 상태 → 컨텍스트 → 외부 스토어 순으로 확장합니다.

### 비교 표

| 방식     | 규모 | 복잡도 | 비고 |
|:---------|:----:|:------:|-----:|
| useState | 소   | 낮음   | 컴포넌트 내부 |
| Context  | 중   | 중간   | 전역/공유 값 |
| Store    | 대   | 높음   | 캐싱/동기화 |

```tsx
function Counter(){
  const [n, setN] = useState(0);
  return (
    <button onClick={() => setN(n+1)}>
      count: {n}
    </button>
  );
}
```

필요하면 서버 상태는 SWR/React Query 같은 전용 도구를 사용해 캐시/동기화 전략을 분리하세요.

