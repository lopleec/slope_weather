# Release builds

## Signing

Public releases use a private production signing key. The optional `-PlocalRelease=true` mode is for local development and is rejected by `:app:checkRelease`.

Create `.signing/signing.properties` with these values, or set the equivalent environment variables:

| Property | Environment variable |
| --- | --- |
| `storeFile` | `SLOPE_RELEASE_STORE_FILE` |
| `storePassword` | `SLOPE_RELEASE_STORE_PASSWORD` |
| `keyAlias` | `SLOPE_RELEASE_KEY_ALIAS` |
| `keyPassword` | `SLOPE_RELEASE_KEY_PASSWORD` |

Relative keystore paths are resolved against `.signing/`. The directory is excluded from version control and release archives. Restrict its access and back up the signing key and passwords securely; the signing identity is needed for future Android updates.

Android updates require the same signing identity. A production-signed APK cannot replace a debug-signed development installation with the same package name; uninstalling an existing installation clears its local places and settings.

## Verification and packaging

```sh
./gradlew :app:checkRelease
node tools/test-map.mjs
python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/prepare-release.py --apk app/build/outputs/apk/release/app-release.apk
```

`checkRelease` runs release unit tests, release Lint, signing verification and an optimized APK build. `tools/test-map.mjs` checks the bundled map controller with deterministic tile events. The packaging tool requires Android SDK Build Tools on `PATH` or under `ANDROID_HOME` / `ANDROID_SDK_ROOT`, or the SDK declared in a local `local.properties`.

The packaging tool rejects a debuggable, unsigned or debug-signed APK; verifies the application ID and version against the project; and checks the manifest and bundled notices. It creates a source archive from an explicit list of project paths and excludes secrets, build outputs, local properties and diagnostic captures. Source archive timestamps are normalized for reproducibility; `SOURCE_DATE_EPOCH` can set the archive timestamp.

Output is written under `dist/<version>/`: APK, source ZIP, SHA-256 checksums, certificate metadata and release notes. The script prepares local artifacts; publishing a GitHub Release is a separate action.

Before publication, inspect the release notes, compare the public signing-certificate fingerprint with the intended signing identity, and test the final signed APK. Preserve the source archive and checksums with the release.

## 中文说明

正式发布使用私有生产签名；`-PlocalRelease=true` 仅用于本地开发，不能通过 `:app:checkRelease`。

在 `.signing/signing.properties` 中配置 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`，或使用上表的环境变量。相对密钥路径基于 `.signing/` 解析。该目录不会进入版本控制和发布归档；应限制访问，并安全备份密钥及密码，后续 Android 更新需要保留签名身份。

Android 更新要求签名身份一致。正式签名 APK 无法直接覆盖同包名的调试签名开发版；卸载已有安装会清除其本地城市及设置。

执行上方构建与打包命令后，`dist/<版本>/` 将包含 APK、源码 ZIP、SHA-256 校验值、公开证书信息及发布说明。源码包采用明确的路径清单，排除私钥、本机配置、构建输出和诊断截图。打包工具只准备本地产物，发布 GitHub Release 是独立操作。

公开发布前，应审阅发布说明、核对正式签名证书指纹，并验证最终签名 APK；源码和校验值应与该版本一同保留。
