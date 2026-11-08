# pleos-side-coach

## 📌 프로젝트 개요
- 목적: (작성 예정)
- 시작일: 2026-11-09
- 기술 스택: (작성 예정)

---

## 🗂️ 코드 구조

```
pleos-side-coach/
├── CLAUDE.md               # Claude Code 진입점, 작업 규칙 로드
├── .claude/rules/rules.md  # 작업 규칙 (워크플로우, Git 컨벤션, 문서화 규칙)
├── github/                 # GitHub App 봇 스크립트 (이슈·브랜치·PR·리뷰)
├── PROJECT_LOG.md          # 개발 일지
└── .gitignore              # github_bot.json(자격증명), __pycache__ 제외
```

- 왜 이 구조인가: claude-setup 템플릿을 그대로 사용해 다른 레포와 같은 작업 규칙·봇을 공유하기 위함

**주요 함수 목록**
| 함수 | 위치 | 한 줄 설명 |
|------|------|-----------|
| get_token() | github/auth.py | GitHub App JWT로 installation token 발급 |
| get_owner_repo() | github/auth.py | git remote origin에서 owner/repo 파싱 |

---

## ✅ 기능 목록

| 기능 | 설명 | 상태 |
|------|------|------|
| claude-setup 배포 (#1) | 작업 규칙·GitHub 봇·개발 일지 템플릿 추가 | ✅ 완료 |

---

## 📋 개발 일지

### #1 — claude-setup 초기 설정 배포 (2026-11-09)

**왜 이 작업을 했나**
> 새 레포에서도 Claude Code가 같은 워크플로우(이슈 → 브랜치 → PR)와 문서화 규칙을 따르도록 하기 위해

**뭘 했나**
> claude-setup의 `deploy.py`로 CLAUDE.md, .claude/rules/rules.md, github/ 봇 스크립트, PROJECT_LOG.md를 복사하고 .gitignore에 github_bot.json을 추가

**어떤 구조를 선택했고 왜**
> 규칙은 `.claude/rules/`에 두고 CLAUDE.md에서 불러오는 구조. 진입점과 규칙 본문을 분리해 템플릿 갱신 시 규칙 파일만 교체하면 되도록 함

**왜 이 방법을 선택했나 (다른 방법은 없었나)**
> 파일을 수동 복사할 수도 있지만 deploy.py를 쓰면 .gitignore 처리까지 일관되게 적용되고 누락이 없음

**배운 것 / 몰랐던 것**
> 빈 레포에는 base 브랜치가 없어 봇의 create_branch가 동작하지 않으므로, main에 초기 커밋을 먼저 push해야 함
> 봇 스크립트를 실행하면 github/__pycache__가 생기므로 .gitignore에 넣어야 함

**에러 & 해결**
> 에러: `Author identity unknown` (커밋 실패)
> 원인: 레포에 git user.name/email 미설정
> 해결: 레포 로컬 설정으로 user.name/email 지정

---

## 🐛 에러 모음

| 에러 | 원인 | 해결 |
|------|------|------|
| Author identity unknown | git 사용자 정보 미설정 | 레포 로컬 `git config user.name/email` 설정 |
| 빈 레포에서 브랜치 생성 불가 | 원격에 base 브랜치(main) 없음 | main에 초기 커밋 push 후 브랜치 생성 |
| __pycache__ 커밋됨 | .gitignore에 미등록 | .gitignore에 `__pycache__/` 추가 후 인덱스에서 제거 |
