# Third-party notices

이 프로젝트는 다음 오픈소스/커뮤니티 자료를 사용하거나 참고했습니다.

## 런타임 라이브러리

- **Chart.js** — MIT License — <https://www.chartjs.org/>
  - `assets/vendor/chart.umd.min.js`
- **pako** — MIT License — <https://github.com/nodeca/pako>
  - `assets/vendor/pako.min.js`

## 세이브 포맷 / 커뮤니티 자료

- **souls_givifier** (jtesta) — BND4 / SL2 AES 구조 참고
- **Elden Ring Nightreign Save Editor** (alfizari) — Nightreign SL2 컨테이너 구조 참고
- **sl2-analyzer** (darthdemono) — Nightreign BND4 / SL2 AES 키 및 컨테이너 설명 참고
- Nightreign SL2 AES 키는 커뮤니티(TKGP / SoulsFormats)에서 공개된 값을 사용했습니다.
- **nightreign-relic-browser** (Metin Çelik, MIT) / **nightreign-relic-vault** (Tony Davis, MIT)
  — 유물 인벤토리 레코드(80B)·itemId→색/심도 카탈로그·접시 슬롯 색 구성 참고
  - `assets/data/relic-catalog.json` (아이템 색/타입·직업별 접시)
  - 유물 파싱 로직은 위 프로젝트의 공개 포맷 설명을 바탕으로 재구현했습니다.
- **Relics Pro** 앱에 포함된 인게임 다국어 이름 팩에서 한국어 유물/효과/접시 표기를 추출해
  `assets/data/relic-ko.json`으로 사용합니다. (게임 원문 표기, 비공식 재배포)
  - 빌드 가중치·중첩/충돌 메타: `assets/data/relic-builds.json`, `assets/data/relic-effect-meta.json`
  - 보스/저널 고정 유물 효과 ID: `assets/data/relic-unique.json` (Relics Pro items)
- 보스·저널 유물의 한글 이름/고정옵션 설명은 [나무위키 밤의 통치자/유물](https://namu.wiki/w/%EC%97%98%EB%93%A0%20%EB%A7%81%20%EB%B0%A4%EC%9D%98%20%ED%86%B5%EC%B9%98%EC%9E%90/%EC%9C%A0%EB%AC%BC)과 교차 확인했습니다.
- 저주 등급·스탯/심도/아츠 가중치는 운영자 인사이트 기반 `assets/data/relic-score-rules.json`을 사용합니다.
- 공격력 옵션은 [아카 수치](https://arca.live/b/fromsoft/160015990)×메타 기댓값으로 보정합니다. 단일 속성공증은 출격 속성부여(~물리 15% 전환) 기준 raw×0.15; 염격·Halo 등 leaf에서 풀가치. 추적자는 물리 위주. 레이디 광륜낫(Halo)은 속성 공격력 ≳ 신성 ≫ 물리. 속검(카리아)은 카리아의 대검 기준·서리 밟기가 best. 무뢰한은 체급(최대HP·스킬쿨·HP저하 경감)과 첫타 강화(일반 물리+2보다 우선) 중심. 복수자는 기도 강화·해당 출격기도 중심(용찬 FP 최우선, 짐승발톱=물리만, 미친불=기도강화 고점, 거인불=화염, 고룡=벼락). 은둔자는 마력 > 마술강화 > 속성, 학교는 보이지않는/휘검/카리아만 갈림(카리아는 생존 소폭↑). 집행자는 쌍안녕/세키로, 동상+피의칼날≫서리밟기, 포효HP 반필수, 상태이상축적 저주 시 어빌HP회복 가점. 학자는 서포트(아군·룬·파우치)·근육(잠재 도·진척억제·동상피칼)·항아리(룬·항아리·화염·파우치/화염항아리·서리밟기). 장의사는 유리목걸이+연속공격 공↑+물리, 주먹/곡검/직검(각 잠재 필수), 곡·직은 기량↑반필수, 아츠후스킬·서리밟기·명중스태미나·생명력.

세이브 파일은 읽기 전용으로만 처리하며, 게임 데이터를 수정하지 않습니다.

## 비공식 고지

이 프로젝트는 FromSoftware, Bandai Namco, Steam과 무관한 비공식 팬 프로젝트입니다.
ELDEN RING NIGHTREIGN 및 관련 상표는 각 권리자에게 있습니다.
