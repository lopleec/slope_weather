# Slope Weather 0.1.19

## English

### Changes

- Refresh weather whenever the app opens or returns to the foreground, with the selected city and current location first.
- Fetch remaining saved places independently of the visible page, with at most two concurrent location requests and shared requests for duplicate coordinates.
- Show cached data immediately, then replace it with current results. City cards display loading progress, observation times and cached-data status after a failed refresh.
- Reprioritize queued work when the selected city changes; protect against late responses after a data-source change or city removal.
- Update device location when the app becomes visible with location permission. Scheduled refresh while the app is closed and home-screen widgets remain unimplemented.

## 简体中文

### 更新

- 每次打开应用或回到前台时自动刷新天气，优先当前所选城市与当前位置。
- 其余收藏城市独立于当前页面继续获取；最多同时请求两个地点，同坐标共用请求。
- 先显示缓存，再用新结果更新；城市卡片显示加载进度、观测时间，并在刷新失败时标注缓存状态。
- 切换城市时调整待处理请求的优先级；切换数据源或删除城市后，防止旧响应覆盖当前状态。
- 获得定位授权时，应用回到可见状态会更新设备位置。应用关闭后的定时刷新及桌面小组件仍暂未实现。
