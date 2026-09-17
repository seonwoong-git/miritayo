# APK 자동 빌드

이 프로젝트에는 `.github/workflows/build-apk.yml`이 포함되어 있습니다.

GitHub 저장소의 `main` 브랜치에 올리면 GitHub Actions가 자동으로:

1. JDK 17 설정
2. Android SDK 35 설치
3. Gradle 8.9 설정
4. `assembleDebug` 실행
5. `Miritayo-v0.2-debug.apk` 생성
6. Actions Artifact로 APK 업로드

를 수행합니다.

수동 실행도 가능합니다: GitHub > Actions > Build Miritayo APK > Run workflow.
