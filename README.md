# Slope Weather · 坡度天气

[English](#english) · [简体中文](#简体中文)

## English

Slope Weather is a native Kotlin and Jetpack Compose weather app for Android 8.0 and later. It combines forecasts, weather maps, air quality, official alerts and astronomical calculations in a Material 3 Expressive interface.

Package: `com.lopleec.slopeweather` · License: [Apache-2.0](LICENSE) · [GitHub Releases](https://github.com/lopleec/slope_weather/releases)

### Features

- Current location and saved places, administrative-area search, coordinate input, multilingual place names, swipe-to-delete with undo, and cached weather when offline. Manual places work without location permission.
- Current conditions, hourly and daily forecasts, short-term precipitation where available, wind, humidity, pressure, visibility, UV, air pollution and pollen. Missing source values remain missing. Celsius/Fahrenheit and km/h, m/s or mph are selectable.
- Independent US, European and Chinese AQI standards. An unavailable standard is identified rather than converted from another index.
- Solar-position-based daytime, nighttime and twilight skies, soft drifting clouds, rain/snow and thunderstorm effects. Card appearance follows the selected app/system theme. Cards can be reordered and hidden; detailed forecast views open from each card.
- Historical RainViewer radar, JMA Japan radar, and NASA GPM IMERG satellite precipitation estimates. Maps use only timestamps published by the source. Satellite estimates can be delayed and are distinct from live radar.
- Regional official alerts and WMO SWIC CAP warnings, with optional background notification checks approximately every three hours. Coverage and Android background scheduling limits apply.
- Local sunrise, sunset, twilight, moon phase and moonrise/set calculations. English, Simplified Chinese, Traditional Chinese and Japanese interfaces.
- [Not yet implemented] Fetch weather data in the background and display it in home-screen widgets.

### Data sources and limitations

| Service | Role |
| --- | --- |
| Open-Meteo | Global forecasts and selectable CMA, JMA, DWD, ECMWF, NOAA GFS, Météo-France, UKMO and KNMI models; air quality, pollen and geocoding |
| NMC | Mainland China station observations, website forecasts, AQI and warnings |
| JMA | Japanese AMeDAS observations, regional forecasts, warnings and radar |
| Bright Sky / DWD | German station forecasts and warnings |
| MET Norway | Global Locationforecast; requires an identifiable contact supplied in settings |
| Hong Kong Observatory | Hong Kong observations, nine-day forecasts and warnings |
| NWS | US forecasts, grid fields and official alerts |
| WMO SWIC | Global point queries for participating services' CAP warnings |
| RainViewer / NASA GIBS | Historical radar and satellite precipitation imagery |

Coverage, forecast horizon, variables and update cadence depend on the upstream service. The free Open-Meteo endpoint is intended for noncommercial use and is rate-limited. Public website endpoints can change without notice. An empty alert query does not guarantee that no warnings exist, and a map without echoes does not prove that it is not raining. Weather animations are decorative illustrations.

Data-service attribution, reuse conditions and operational limits are documented in [third-party notices](docs/THIRD_PARTY_NOTICES.md). Software component versions and licences are listed in [open-source licences](docs/OPEN_SOURCE_LICENSES.md) and in the app's About screen.

### Build and test

Requirements: Android SDK 36.1, JDK 21 recommended, and the bundled Gradle Wrapper. Set `ANDROID_HOME` or create a local `local.properties` pointing to the SDK. Node.js is needed only for the map-controller regression test; Python 3 is needed for source packaging and optional live probes.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintRelease
node tools/test-map.mjs
python3 -m unittest discover -s tools -p 'test_*.py'
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Tests cover source parsing, missing values, complete local-day accumulations including daylight saving changes, alert handling, cache integrity, map playback, card details and animation behaviour. Optional read-only service probes use public test coordinates:

```sh
python3 tools/smoke_sources.py
```

### Release builds

Configure a private signing key as described in [release instructions](docs/RELEASING.md), then run:

```sh
./gradlew :app:checkRelease
node tools/test-map.mjs
python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/prepare-release.py --apk app/build/outputs/apk/release/app-release.apk
```

The packaging tool validates the APK and produces a signed APK, a source archive, checksums and public release notes. Signing keys, passwords, local SDK paths, build caches and private diagnostic captures are excluded from the source archive.

### Privacy

No account, advertising or analytics SDK is included. Coordinates are sent to weather, air-quality, alert and geocoding services when those features run; searches are sent to search providers, and maps request tiles from their providers. Places, settings and weather caches are stored in the app's private storage. Location and notification permissions are optional. Background location permission is not requested.

### Contributing and licensing

Bug reports should include the app version, Android version, selected data source and reproduction steps. Avoid including precise personal coordinates or private signing material in public reports.

Copyright 2026 Lopleec. Application source is licensed under [Apache-2.0](LICENSE); third-party software and data retain their respective licences. See [NOTICE](NOTICE).

## 简体中文

坡度天气是一款使用 Kotlin 和 Jetpack Compose 开发的原生 Android 天气应用，支持 Android 8.0 及以上版本。应用采用 Material 3 Expressive 界面，提供天气预报、天气地图、空气质量、官方预警与天文计算。

包名：`com.lopleec.slopeweather` · 项目许可证：[Apache-2.0](LICENSE) · [GitHub 下载与发布](https://github.com/lopleec/slope_weather/releases)

### 功能

- 当前位置与收藏城市、行政区搜索、经纬度输入、多语言地名、左滑删除与撤销，以及断网时的天气缓存。手动添加城市无需定位权限。
- 当前天气、逐小时和逐日预报、源支持时的短时降水，以及风、湿度、气压、能见度、UV、空气污染物与花粉。缺失值保留为缺失；支持摄氏度／华氏度，以及 km/h、m/s、mph。
- 美国、欧洲和中国 AQI 标准独立选择；所选标准不可用时明确提示，不用另一种指数换算顶替。
- 按城市当前太阳位置变化的昼夜与晨昏天空、柔和浮云、雨雪和雷暴效果。卡片配色跟随应用／系统主题；支持拖放排序、隐藏及独立详情页。
- RainViewer 历史雷达、JMA 日本雷达与 NASA GPM IMERG 卫星降水估计。地图只使用服务公布的实际时间帧；卫星估计可能有延迟，与实时雷达不同。
- 区域官方预警与 WMO SWIC CAP 预警，可选约每三小时执行的后台通知检查。实际覆盖和通知及时性受到源接入情况及 Android 后台限制影响。
- 本地计算日出、日落、晨昏、月相及月出月落。界面支持英语、简体中文、繁体中文和日语。
- [暂未实现] 后台刷新获取天气数据并使用桌面小组件显示。

### 数据源与限制

| 服务 | 用途 |
| --- | --- |
| Open-Meteo | 全球预报及可选 CMA、JMA、DWD、ECMWF、NOAA GFS、Météo-France、UKMO、KNMI 模式；空气质量、花粉及地理编码 |
| 中央气象台 NMC | 中国大陆站点观测、网页预报、AQI 与预警 |
| 日本气象厅 JMA | AMeDAS 观测、区域预报、预警与日本雷达 |
| Bright Sky / DWD | 德国站点预报与预警 |
| MET Norway | 全球 Locationforecast，须在设置中填写可识别的联系信息 |
| 香港天文台 | 香港观测、九天天气预报与预警 |
| NWS | 美国预报、网格字段与官方预警 |
| WMO SWIC | 对参与服务的 CAP 预警进行全球坐标查询 |
| RainViewer / NASA GIBS | 历史雷达与卫星降水图像 |

覆盖范围、预报天数、字段及更新时间由上游服务决定。Open-Meteo 免费接口面向非商业用途并有限流；公开网站接口可能调整。未查询到预警不代表一定没有预警，地图没有回波也不代表没有降雨。天气动效仅用于示意。

数据署名、使用条件及服务限制见[第三方声明](docs/THIRD_PARTY_NOTICES.md)。软件组件版本与许可证见[开源组件清单](docs/OPEN_SOURCE_LICENSES.md)，应用内“关于”也提供完整说明。

### 构建与测试

需要 Android SDK 36.1，建议使用 JDK 21；项目已包含 Gradle Wrapper。通过 `ANDROID_HOME` 或本地 `local.properties` 配置 SDK 路径。地图控制器回归测试需要 Node.js，源码打包与可选实网检查需要 Python 3。

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintRelease
node tools/test-map.mjs
python3 -m unittest discover -s tools -p 'test_*.py'
```

调试 APK：`app/build/outputs/apk/debug/app-debug.apk`。

测试覆盖源解析、缺测处理、含夏令时切换的完整当地日累计值、预警处理、缓存完整性、地图回放、卡片详情和动效行为。可选的只读实网检查使用公开测试坐标：

```sh
python3 tools/smoke_sources.py
```

### 发布构建

按照[发布说明](docs/RELEASING.md)配置私有签名密钥，然后执行：

```sh
./gradlew :app:checkRelease
node tools/test-map.mjs
python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/prepare-release.py --apk app/build/outputs/apk/release/app-release.apk
```

打包工具会核验 APK，并生成正式签名 APK、源码归档、校验值和公开发布说明。源码归档不包含签名密钥、密码、本机 SDK 路径、构建缓存或私有诊断截图。

### 隐私

应用不包含账号、广告或统计 SDK。相关功能运行时，会向天气、空气质量、预警和地理编码服务发送坐标；搜索词发送至搜索服务，地图向相应服务请求瓦片。城市、设置和天气缓存保存在应用私有存储中。定位及通知权限均为可选，不申请后台定位权限。

### 参与与许可

反馈问题时请提供应用版本、Android 版本、所选数据源及复现步骤；公开反馈中应避免包含精确私人坐标或签名材料。

Copyright 2026 Lopleec。应用源码使用 [Apache-2.0](LICENSE)，第三方软件和数据分别保留原有许可证，详见 [NOTICE](NOTICE)。
