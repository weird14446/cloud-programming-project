# Chat App (Java, 콘솔 & Swing)

다중 클라이언트 서버, 콘솔 클라이언트, 현대적인 Swing GUI 클라이언트를 단일 소스 파일에 담은 가벼운 TCP 채팅 애플리케이션입니다.

## 주요 기능
- 닉네임 등록 및 전체 브로드캐스트를 지원하는 소켓 기반 채팅 서버
- 터미널에서 빠르게 대화할 수 있는 콘솔 클라이언트
- Discord 스타일 레이아웃, 반응형 패널, 상태 표시가 있는 Swing GUI 클라이언트
- `/quit` 명령으로 깔끔하게 종료
- Gradle(Java Toolchain 25)로 패키징

## 필요 사항
- Java 25 (Gradle에서 툴체인 자동 설정)
- 추가 외부 의존성 없음; 필요 시 `lib/`에 JAR 추가 가능

## 빠른 시작
빌드(선택 사항, Gradle Wrapper가 필요한 도구를 내려받습니다):
```bash
./gradlew build
```

### 서버 실행
```bash
./gradlew run --args "server 5000"
```

### 콘솔 클라이언트 실행
```bash
./gradlew run --args "client localhost 5000 alice"
```

### GUI 클라이언트 실행
```bash
./gradlew run --args "client-gui localhost 5000 alice"
```
(`client-gui` 대신 `gui`도 사용 가능합니다.)

## 사용 팁
- 첫 연결 시 입력한 닉네임을 서버로 전송합니다.
- 메시지를 입력해 Enter로 전송하며, `/quit`로 종료합니다.
- GUI는 연결 상태를 표시하고 창이 좁아지면 일부 패널을 숨겨줍니다.

## 프로젝트 구조
- `src/Main.java` — 진입점, 서버, 콘솔 클라이언트, GUI 클라이언트, 스타일 헬퍼 포함
- `build.gradle` — 애플리케이션 설정, 툴체인(Java 25), 매니페스트 설정
- `settings.gradle` — 프로젝트 이름 및 Foojay 툴체인 리졸버 플러그인
- `gradlew`, `gradlew.bat`, `gradle/` — Gradle Wrapper

## 라이선스
명시되지 않았습니다(별도 LICENSE 파일 없음). 필요 시 라이선스를 추가하세요.
