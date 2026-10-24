# Slope Weather 0.1.17

## English

Native Android weather forecasts, weather maps, air quality, official alerts and astronomical calculations. Supports Android 8.0 and later, with English, Simplified Chinese, Traditional Chinese and Japanese interfaces.

### Changes

- Prevent city-search crashes when multiple gazetteers return the same coordinate with different names.
- Calculate complete daily rain and snowfall totals using local day boundaries, including 23-hour and 25-hour daylight saving days. Incomplete days retain missing totals.
- Parse CAP warnings with XML namespace prefixes and preserve bulletin issue times. Coordinate-only locations use global CAP coverage instead of an assumed national warning area. Notification deduplication distinguishes new bulletins that reuse an alert identifier and keeps a bounded receipt history.
- Preserve freshly fetched weather when cache storage is unavailable; replace cached weather atomically, recover from malformed JSON caches and bound HTTP cache storage and response size.
- Honour approximate location permission in balanced mode, handle location-name lookup failures and cancel obsolete city-preview requests.
- Keep cached radar frames playable after a map pan that loads no new tiles. Pause playback when the app leaves the foreground.
- Return to the city list when a restored weather or map page has no selected location after Android recreates the app process.
- Validate radar origins and frame paths, and update jsoup to 1.23.1.
- Provide production signing verification, explicit source packaging and an English-first bilingual README.

### Distribution

The APK uses production signing. The source archive excludes private signing material, local SDK configuration, build caches and diagnostic captures. SHA256SUMS contains the release-asset hashes; BUILD-INFO.json records package, version and public signing-certificate metadata.

Forecast and alert coverage depends on upstream services. NASA IMERG displays delayed satellite precipitation estimates. Official alert checks and notifications are subject to source availability and Android scheduling limits.

## 简体中文

原生 Android 天气预报、天气地图、空气质量、官方预警与天文计算应用，支持 Android 8.0 及以上版本，提供英语、简体中文、繁体中文和日语界面。

### 更新

- 修复多个地理编码服务返回同坐标、不同名称的结果时可能发生的搜索列表崩溃。
- 按当地日期边界汇总完整日的雨量和降雪量，正确处理夏令时切换的 23／25 小时日；不完整日期保留缺测。
- 支持带 XML 命名空间前缀的 CAP 预警并保留公告发布时间；缺少国家信息的坐标使用全球 CAP 查询，不推定国家预警范围；通知去重可区分复用同一 ID 的新公告，限制历史记录数量。
- 缓存存储不可用时保留已取得的新天气；采用原子替换、损坏 JSON 恢复，并限制 HTTP 缓存及响应大小。
- 平衡定位模式尊重大致位置授权，处理地名查询失败，取消过期的城市预览请求。
- 修复地图平移未请求新瓦片后缓存雷达帧可能无法继续回放的问题；应用离开前台时暂停回放。
- Android 重建应用进程后，恢复的天气或地图页缺少所选城市时返回城市列表，避免出现空白页。
- 核验雷达来源与帧路径，将 jsoup 更新至 1.23.1。
- 增加正式签名核验、明确的源码打包范围，以及英文在前的双语 README。

### 分发

APK 使用正式发布签名。源码包不包含私有签名材料、本机 SDK 配置、构建缓存和诊断截图。SHA256SUMS 提供发布文件校验值，BUILD-INFO.json 记录包名、版本及公开签名证书信息。

预报和预警覆盖由上游服务决定。NASA IMERG 提供有延迟的卫星降水估计；官方预警检查和通知受到源可用性与 Android 调度限制影响。
