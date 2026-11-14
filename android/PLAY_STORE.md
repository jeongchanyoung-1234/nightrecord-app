# Google Play 등록 체크리스트

패키지: `app.nightrecord` · 버전: **0.6.0 (106)**

## 업로드 파일

| 파일 | 경로 |
| --- | --- |
| AAB (Play 필수) | `android/app/build/outputs/bundle/release/app-release.aab` 또는 `downloads/nightrecord-play.aab` |
| 릴리스 APK (원스토어 교체용) | `downloads/nightrecord-release.apk` |
| 업로드 키 | `android/keystore/nightrecord-upload.jks` + `android/keystore.properties` (**백업 필수, git 제외**) |

Play Console → 앱 만들기 → **Play App Signing** 사용 → 위 AAB 업로드.

## 스토어 등록정보 (원스토어 문구 재사용)

- 앱 이름: 밤의 기록
- 짧은 설명: 엘든링 밤의 통치자 전적·유물 통계 앱 (nightrecord.app 회원용)
- 전체 설명: 원스토어에 등록한 상품설명 그대로 사용 가능
- 카테고리: 생산성 / 도구 등 적절한 항목

## 그래픽

| 항목 | 파일 |
| --- | --- |
| 고해상도 아이콘 512×512 | `downloads/onestore/icon_512.png` |
| 기능 그래픽 1024×500 | `downloads/onestore/feature_graphic_1024x500.png` |
| 휴대폰 스크린샷 | `downloads/onestore/screenshot_01_login.png` ~ `08_matching.png` (최소 2장) |

## 정책·등급

- 개인정보처리방침 URL: **https://nightrecord.app/privacy.html**
- Data safety
  - 수집: 계정 정보, 앱 활동(전적·프로필), 기기·기타(앱 내 토큰 저장)
  - 세이브 원본·생체 원본·광고 ID 수집 없음
  - 데이터가 판매되지 않음, 광고 SDK 없음
- 콘텐츠 등급 설문 완료 (게임 전적 팬 도구, 인앱 결제·광고 없음)
- 대상 연령: 일반 / 14세 미만 모집 아님
- 광고 선언: 광고 없음
- 앱 액세스(권한): INTERNET, USE_BIOMETRIC — 로그인·생체 잠금 목적 설명

## 권장 출시 순서

1. 내부 테스트 트랙에 AAB 업로드 → 본인 계정 설치 확인
2. 비공개/공개 테스트 (선택)
3. 프로덕션 출시
4. Play 상세 URL을 `assets/config.js`의 `PLAY_STORE_URL`에 넣어 사이트 Android 토글에 Play 버튼 표시

## 서명 참고

- 기존 사이트/원스토어 APK는 **Debug 서명**이었습니다. Play·이후 원스토어 업데이트는 이 릴리스 키로 맞추세요.
- 업로드 키를 분실하면 업데이트가 매우 어렵습니다. `keystore/`와 `keystore.properties`를 안전한 곳에 백업하세요.
