# HaloVision V3

시각장애인 및 저시력 사용자가 스마트폰 화면의 정보를 더 쉽게 **보고, 읽고, 이해하고, 필요한 요소를 찾을 수 있도록 돕는 AI 기반 접근성 보조 앱**입니다.

HaloVision V3는 단순히 화면을 확대하거나 글자를 크게 보여주는 것을 넘어, 사용자가 현재 보고 있는 화면 위에서 필요한 순간에 접근성 기능을 호출할 수 있도록 설계되었습니다.

> 화면을 바꾸는 접근성에서, 필요한 순간에 개입하는 접근성으로.

---

## 프로젝트 개요

기존 HaloVision V2는 사용자의 시야 특성에 맞춰 문서 내용을 재배치하는 방식에 초점을 두었습니다.

실사용자 테스트를 통해 다음과 같은 점을 확인했습니다.

- 사용자는 이미 스마트폰의 확대, 글자 크기, 고대비, TalkBack 등의 기능을 활용하고 있음
- 시야 유형에 따라 화면을 고정적으로 재배치하는 방식만으로는 실제 사용성을 충분히 높이기 어려움
- 화면의 내용을 이해하기 어려울 경우 사진을 촬영한 뒤 생성형 AI에 질문하는 과정을 사용하지만, 촬영 → 저장 → AI 앱 실행 → 사진 첨부 → 질문 → 사진 삭제 과정이 번거로움
- 단순한 화면 확대보다 현재 화면의 의미를 이해하고 원하는 정보를 빠르게 찾는 기능에 대한 필요성이 큼

이에 따라 V3에서는 기존 앱 위에서 필요할 때 호출하여 사용하는 **상황 적응형 온디맨드 접근성 보조 시스템**으로 방향을 확장했습니다.

---

## 주요 기능

### 1. HALO 접근성 오버레이

Android AccessibilityService를 기반으로 다른 앱을 사용하는 중에도 HALO 버튼을 호출할 수 있습니다.

HALO 메뉴에서 주요 접근성 기능을 바로 실행할 수 있습니다.

- 보기 편하게
- 중요한 내용만 보기
- 화면 읽어주기
- 질문하기
- 설정
- 카메라

---

### 2. 큰 글자로 보기

현재 화면에서 읽을 수 있는 텍스트를 추출한 뒤 사용자가 설정한 글자 크기에 맞춰 별도의 읽기 쉬운 화면으로 제공합니다.

텍스트 추출에는 다음 정보를 함께 활용합니다.

- Android Accessibility Node Tree
- Google ML Kit OCR

Node Tree만으로 충분한 텍스트를 확보하기 어려운 화면에서는 OCR을 활용합니다.

---

### 3. 화면 읽어주기

현재 화면의 텍스트를 분석하고 Android TTS를 이용해 음성으로 읽어줍니다.

사용자는 설정에서 음성 읽기 속도를 조절할 수 있습니다.

---

### 4. 중요한 내용만 보기

현재 화면에서 수집한 정보를 AI가 분석하여 사용자가 우선적으로 확인해야 할 핵심 내용을 간단하게 제공합니다.

Accessibility Node Tree 정보가 부족한 경우 화면 이미지를 활용한 Vision 분석을 추가로 수행합니다.

---

### 5. 화면 기반 질문하기

현재 보고 있는 화면을 기준으로 자연어 질문을 할 수 있습니다.

예시:

- 이 화면에서 중요한 내용이 뭐야?
- 신청 기간이 언제야?
- 이 페이지가 무슨 내용이야?
- 결제 버튼이 어디 있어?

화면 정보가 충분하지 않을 경우 Vision 분석을 추가하여 답변을 생성합니다.

---

### 6. UI 요소 찾기

사용자가 자연어로 찾고 싶은 버튼이나 UI 요소를 말하면 Accessibility Node 정보를 기반으로 해당 요소의 위치를 탐색합니다.

예시:

- 로그인 버튼 찾아줘
- 다음 버튼 어디 있어?
- 검색 버튼 찾아줘

탐색된 UI 요소는 화면 위에 시각적으로 강조하여 표시하며 필요한 경우 자동 스크롤을 수행합니다.

---

### 7. HaloVision 카메라

앱 내부에 CameraX 기반 카메라 기능을 제공합니다.

별도의 사진 촬영과 저장 과정 없이 카메라 화면을 보면서 HALO 접근성 기능을 사용할 수 있도록 설계했습니다.

이를 통해 기존의

촬영 → 갤러리 저장 → AI 앱 실행 → 사진 첨부 → 질문 → 사진 삭제

과정을 줄이는 것을 목표로 합니다.

---

## 시스템 구조

```text
┌─────────────────────────────────────┐
│            Android Device           │
│                                     │
│  ┌───────────────────────────────┐  │
│  │       Flutter Application     │  │
│  │                               │  │
│  │  사용자 설정 / 문서 UI / 화면 │  │
│  └───────────────────────────────┘  │
│                 │                   │
│  ┌───────────────────────────────┐  │
│  │ Android AccessibilityService  │  │
│  │                               │  │
│  │ Node Tree / Overlay / TTS     │  │
│  │ STT / Screenshot / UI 탐색    │  │
│  └───────────────────────────────┘  │
│          │              │           │
│          │              │           │
│     ML Kit OCR      CameraX         │
└──────────┼──────────────────────────┘
           │
           ▼
┌─────────────────────────────────────┐
│          FastAPI Backend            │
│                                     │
│   화면 분석 / 질문 처리 / AI 요청   │
└─────────────────┬───────────────────┘
                  │
                  ▼
          ┌───────────────┐
          │  OpenAI API   │
          │ Text + Vision │
          └───────────────┘
```

---

## 기술 스택

### Mobile

- Flutter
- Dart
- Kotlin
- Android AccessibilityService
- CameraX
- Google ML Kit Text Recognition
- Android Text To Speech
- Android SpeechRecognizer

### Backend

- Python
- FastAPI
- SQLAlchemy
- PostgreSQL

### AI

- OpenAI API
- Text-based screen understanding
- Vision-based screen understanding
- Context-based question answering

### Deployment

- Render
- GitHub

---

## 프로젝트 구조

```text
halovision/
│
├── android/
│   └── app/src/main/kotlin/
│       └── com/example/halovision_app/
│           ├── MainActivity.kt
│           ├── HaloAccessibilityService.kt
│           └── HaloCameraActivity.kt
│
├── backend/
│   ├── server.py
│   ├── database.py
│   ├── models.py
│   └── storage.py
│
├── lib/
│   └── main.dart
│
├── assets/
│   └── images/
│
└── pubspec.yaml
```

---

## 실행 방법

### Flutter 앱

```bash
flutter pub get
flutter run
```

연결된 Android 기기 확인:

```bash
flutter devices
```

특정 기기에서 실행:

```bash
flutter run -d DEVICE_ID
```

---

## Android 접근성 서비스 설정

HaloVision V3의 HALO 기능을 사용하려면 Android 접근성 권한이 필요합니다.

```text
설정
→ 접근성
→ 설치된 앱
→ HaloVision
→ 사용
```

카메라 및 음성 기능 사용 시 카메라와 마이크 권한도 허용해야 합니다.

---

## V2 → V3 발전 방향

### HaloVision V2

시야 특성에 따라 문서 콘텐츠를 재배치하는 개인화 UI 중심

### HaloVision V3

기존 앱 위에서 필요한 순간에 동작하는 상황 맞춤형 접근성 보조 중심

```text
V2
시야 특성 분석
→ 화면 재배치
→ 읽기 편한 UI 제공

V3
현재 화면 인식
→ 사용자 요청 이해
→ 필요한 접근성 기능 실행
→ 정보 이해 및 행동 보조
```

V3의 핵심 흐름은 다음과 같습니다.

**보기 → 읽기 → 이해 → 행동**

---

## 기존 접근성 기능과의 차이

HaloVision은 Android의 기존 접근성 기능을 대체하는 것이 아니라 함께 사용하는 것을 목표로 합니다.

기존 기능이 주로

- 화면 확대
- 글자 크기 조절
- 고대비
- 화면 읽기

등을 제공한다면 HaloVision은 여기에

- 현재 화면의 의미 이해
- 핵심 내용 요약
- 화면 기반 자연어 질문
- 원하는 UI 요소 탐색
- 화면과 카메라를 연결한 AI 접근성 보조

기능을 추가합니다.

---

## 프로젝트 상태

현재 HaloVision V3의 핵심 프로토타입 기능을 구현한 상태입니다.

- HALO 접근성 오버레이
- 화면 텍스트 수집
- ML Kit OCR
- 화면 읽기
- 큰 글자 보기
- 중요 내용 요약
- 자연어 질문
- UI 요소 탐색 및 강조
- CameraX 기반 HaloVision 카메라
- 다양한 Android 화면 크기 대응

현재는 Android 실제 기기를 중심으로 기능 및 호환성 테스트를 진행하고 있습니다.

---

## 향후 개발 방향

- 다양한 Android 기기 호환성 검증
- OCR 및 화면 인식 정확도 개선
- 실제 시각장애인 사용자 대상 사용성 평가
- UI 요소 탐색 정확도 개선
- AI 호출 비용 최적화
- 배포용 Android 빌드 안정화
- 접근성 서비스와 AI 기능의 통합 사용성 개선

---

## Project

**HaloVision V3 — AI-based Accessibility Assistant for Low-Vision Users**
