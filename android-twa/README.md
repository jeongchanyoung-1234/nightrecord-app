# Android TWA (site-hosted APK)

Play 스토어 없이 `https://nightrecord.app/downloads/nightrecord.apk` 로 배포합니다.

## 빌드

```bash
export JAVA_HOME="$HOME/.local/jdk/jdk-17.0.21+9"
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$PATH"
export BUBBLEWRAP_KEYSTORE_PASSWORD="$(cat .keystore-password)"
export BUBBLEWRAP_KEY_PASSWORD="$(cat .keystore-password)"

cd android-twa
npx bubblewrap build
cp app-release-signed.apk ../downloads/nightrecord.apk
```

키스토어·비밀번호 파일은 git에 올리지 마세요.
