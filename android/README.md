# 밤의 기록 — Android (Kotlin)

패키지: `app.nightrecord`  
스토어: [원스토어](https://m.onestore.co.kr/v2/ko-kr/app/0001012391)

모바일은 **세이브 업로드가 없습니다.**  
[nightrecord.app](https://nightrecord.app)에서 가입·세이브 분석한 계정으로 로그인해 서버 전적·유물·검색을 봅니다.  
앱 UI는 **한국어만** 지원합니다.

## 기능

- 로그인 / 토큰 갱신
- 전적 (개요·직업·보스·매칭·기록)
- 플레이어 검색 · 공개 프로필
- 유물 추천 조합기
- 마이페이지 (태그·아바타·비밀번호)
- 생체 인증 잠금 · 홈 위젯 · Room 오프라인 캐시

## 모듈

| 모듈 | 역할 |
| --- | --- |
| `app` | Compose UI, API, Room, 생체/위젯 |
| `core-parser` | 서버 원정 JSON → 통계 집계 |

> 공개 GitHub 스냅샷에는 **세이브 파서·유물 추천 엔진·가중치 JSON이 포함되지 않습니다.**  
> 전체 빌드·배포는 운영 환경 또는 [원스토어](https://m.onestore.co.kr/v2/ko-kr/app/0001012391) / 사이트 APK를 이용하세요.

## 빌드 (로컬)

```bash
export JAVA_HOME="$HOME/.local/jdk/jdk-17.0.21+9"   # JDK 17
export ANDROID_HOME="$HOME/Android/Sdk"
cd android
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

필요: Android SDK (compileSdk 36), JDK 17.
