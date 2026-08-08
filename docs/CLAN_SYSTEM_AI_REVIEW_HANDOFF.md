# 宗门系统 AI 检查与接手说明

这份文档交给下一个 AI 使用。请先完整检查现有实现，不要直接重写，E:/250/junklord-companion/phone-app/docs/CLAN_SYSTEM_AI_REVIEW_HANDOFF.md也不要假设当前实现已经完全正确。

## 1. 项目位置

手机端 Android 项目：

```text
E:\250\junklord-companion\phone-app
```

服务端项目：

```text
E:\250\junklord-server
```

服务端核心文件：

```text
E:\250\junklord-server\src\server.js
```

会战设计参考：

```text
E:\250\junklord-world\docs\CLAN_WAR_NEXT_IMPLEMENTATION.md
```

## 2. 当前目标

请全面检查手机端宗门系统和报名制宗门会战是否正确、完整、稳定，并修复能通过代码和本地构建确认的问题。

不要只检查能否编译。需要同时检查：

- Retrofit 接口是否与服务端路由、参数和 JSON 完全匹配。
- 数据模型是否允许服务端可为空字段。
- 宗门页面权限判断是否与服务端一致。
- 报名、取消报名和报名名单流程是否正确。
- 未报名玩家是否确实不能参加会战或移动。
- 地图、移动、加速、复活、冷却、历史战绩是否正确处理。
- 网络错误、HTTP 400/403/404 和字段缺失时是否会崩溃。
- 页面生命周期、倒计时和重复请求是否有泄漏或状态问题。
- XML 布局在常见手机尺寸下是否存在遮挡、溢出或无法滚动问题。

## 3. 当前相关文件

API 与模型：

```text
app/src/main/java/com/junklord/world/api/ApiModels.kt
app/src/main/java/com/junklord/world/api/JunklordApiService.kt
app/src/main/java/com/junklord/world/api/ApiClient.kt
```

宗门页面：

```text
app/src/main/java/com/junklord/world/ui/ClanListActivity.kt
app/src/main/java/com/junklord/world/ui/ClanDetailActivity.kt
app/src/main/java/com/junklord/world/ui/ClanWarActivity.kt
```

布局：

```text
app/src/main/res/layout/activity_clan_list.xml
app/src/main/res/layout/activity_clan_detail.xml
app/src/main/res/layout/activity_clan_war.xml
app/src/main/res/layout/item_clan.xml
app/src/main/res/layout/item_clan_member.xml
```

入口相关：

```text
app/src/main/AndroidManifest.xml
app/src/main/java/com/junklord/world/ui/MainActivity.kt
app/src/main/res/layout/activity_main.xml
app/src/main/res/menu/bottom_nav_menu.xml
```

## 4. 已接入的功能

当前代码声称已实现以下功能，请逐项验证，不要只相信本说明：

### 宗门基础功能

- 获取宗门列表。
- 创建宗门。
- 加入宗门。
- 查看自己的宗门详情。
- 修改宗门公告。
- 退出宗门。
- 解散宗门。
- 宗主或长老移出成员。

### 会战报名

- 查询当前玩家报名状态。
- 报名会战。
- 取消报名。
- 显示本宗门报名人数。
- 宗主或长老查看报名名单。

### 会战页面

- 先请求会战状态，再决定是否显示地图。
- 区分未加入宗门、报名宗门不足 4 个、已有会战等状态。
- 未出现在 `war.players` 中的玩家显示为未报名，不能移动。
- 显示段位、会战状态、剩余时间、个人贡献、复活丹和加速器。
- 显示 8x8 地图、宗门归属、封锁格、玩家数量和自己位置。
- 移动前做基本前置校验。
- 支持普通移动、消耗加速器移动、加速当前移动和复活。
- 显示宗门分数榜。
- 显示最近历史战绩。
- 本地每秒刷新剩余时间、移动倒计时和行动冷却。

## 5. 必须对照的服务端接口

请直接阅读 `E:\250\junklord-server\src\server.js` 中的实际实现，重点检查以下接口：

```http
POST /api/junklord/clan/create
POST /api/junklord/clan/join
POST /api/junklord/clan/leave
POST /api/junklord/clan/kick
POST /api/junklord/clan/dissolve
POST /api/junklord/clan/announce
GET  /api/junklord/clan/info/{playerId}
GET  /api/junklord/clan/list
GET  /api/junklord/clan/leaderboard

POST /api/junklord/clan/war/signup
POST /api/junklord/clan/war/cancel-signup
GET  /api/junklord/clan/war/signup-status?playerId=...
GET  /api/junklord/clan/war/signups?playerId=...
GET  /api/junklord/clan/war/status?playerId=...
GET  /api/junklord/clan/war/map?playerId=...
POST /api/junklord/clan/war/move
POST /api/junklord/clan/war/speedup
POST /api/junklord/clan/war/revive
GET  /api/junklord/clan/war/history?playerId=...
```

特别注意 `/clan/war/map` 当前返回结构包含嵌套 `war`，同时还包含顶层 `grid`、`players` 和 `scores`。不要把它误当作完全扁平的 `ClanWarInfo`。

## 6. 优先检查的风险点

### 6.1 HTTP 错误正文

`ApiClient.safeApiCall` 对非 2xx 响应目前直接返回原始 `errorBody` 字符串。页面可能显示完整 JSON，例如：

```json
{"success":false,"error":"forbidden"}
```

检查是否应该统一解析服务端 `error` 字段并映射为用户可读提示。

### 6.2 时间解析

`ClanWarActivity` 当前使用 `SimpleDateFormat` 解析两种 UTC 格式：

```text
yyyy-MM-dd'T'HH:mm:ss'Z'
yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
```

检查服务端是否可能返回其他 ISO 8601 形式，例如不同毫秒位数或时区偏移。如果可能，改成更可靠且兼容 `minSdkVersion 19` 的方案。

### 6.3 本地倒计时

当前倒计时每秒重新调用 `showWar(currentWar)`，会同时重新绘制整个 8x8 地图和分数榜。检查性能是否合理。更好的方案可能是只更新状态文本和按钮状态，并定期向服务端刷新完整状态。

### 6.4 服务端状态刷新

本地倒计时不会自动获取其他玩家移动、占格、击杀和分数变化。目前需要手动刷新才能看到远端变化。检查是否应该在 `fighting` 状态下每隔若干秒轮询 `/status`。

### 6.5 加速按钮逻辑

移动弹窗同时提供“普通移动”和“加速移动”，后者会在发起移动时消耗加速器并缩短初始时间。

“加速当前移动”按钮调用 `/speedup`，会再次消耗加速器并将剩余时间减半。确认产品设计是否允许同一次移动先加速发起，再二次加速。

### 6.6 踢人入口

当前成员列表通过点击整行触发移出确认，没有显式管理图标或按钮。检查是否容易误触，是否应增加可见的管理控件。

权限必须与服务端保持一致：

- 宗主不能被移出。
- 宗主可以移出其他成员。
- 长老只能移出普通成员。
- 普通成员不能移出任何人。

### 6.7 创建和加入状态

如果玩家已经属于一个宗门，宗门列表仍可能显示创建和加入按钮。虽然服务端会拒绝，但客户端应检查是否需要隐藏或禁用这些操作。

### 6.8 宗主退出问题

服务端代码含有以下限制：宗主在宗门有其他成员时不能直接退出，并返回 `owner must transfer or dissolve first`。

但服务端当前没有看到转让宗主接口。检查产品逻辑是否应只允许宗主解散，或需要补服务端转让接口。不要仅在客户端伪造转让。

### 6.9 会战历史完整度

当前手机端历史战绩只展示段位、赛季和各宗门占格/击杀，未展示排名、奖励钻石、胜者、结束原因等字段。先确认服务端实际返回哪些字段，再决定是否补模型和 UI。

### 6.10 地图数据防御

检查以下异常数据时页面是否稳定：

- `grid` 为空。
- `grid` 不是 8x8。
- 某个 cell 为缺失位置。
- 宗门颜色为空或不是合法颜色。
- 玩家坐标越界。
- `war.players` 中没有当前玩家。
- `actionReadyAt` 或 `arriveAt` 无法解析。

## 7. 必须运行的验证命令

服务端语法检查：

```powershell
cd E:\250\junklord-server
node --check src/server.js
```

手机端构建：

```powershell
cd E:\250\junklord-companion\phone-app
.\gradlew assembleDebug
```

当前一次已知构建结果：`assembleDebug` 成功。但下一个 AI 修改后必须重新运行，不能沿用旧结果。

## 8. 建议的实机测试矩阵

至少准备 4 个宗门，每个宗门至少 1 个报名玩家。建议同时准备一个未报名成员和一个宗主/长老账号。

### 基础宗门

1. 创建宗门。
2. 加入宗门。
3. 查看宗门详情和成员列表。
4. 宗主或长老修改公告。
5. 长老移出普通成员。
6. 验证长老不能移出宗主或其他长老。
7. 普通成员退出宗门。
8. 宗主解散宗门。

### 报名

1. 普通成员报名。
2. 报名按钮变为取消报名。
3. 报名人数增加。
4. 取消报名后人数减少。
5. 宗主或长老查看报名名单。
6. 普通成员不能访问管理名单。
7. 玩家退出、被踢或宗门解散后，未来报名应被服务端自动取消。

### 会战生成

1. 报名宗门不足 4 个时显示正确提示。
2. 4 个宗门满足条件后生成会战。
3. 未报名成员可以看到会战状态，但不能移动或领奖。
4. 报名成员出现在 `war.players` 中。

### 会战操作

1. 只能上下左右移动一格。
2. 不能移动到封锁格。
3. 准备中和已结束状态不能移动。
4. 行动冷却期间不能移动。
5. 移动中不能再次移动。
6. 同宗门同格达到上限时服务端拒绝。
7. 没有加速器时服务端拒绝加速。
8. 阵亡玩家不能移动。
9. 有复活丹时可以复活。
10. 没有复活丹时不能复活。
11. 操作成功后客户端使用服务端返回的 `war` 刷新。

### 结算和历史

1. 会战结束后状态变为 `finished`。
2. 分数榜与服务端一致。
3. 历史战绩出现本场记录。
4. 奖励只通过服务端邮件发放。
5. 手机端不得直接修改钻石、金币、钥匙或其他资源。

## 9. 工作区注意事项

当前手机端工作区已有未提交改动和未跟踪文件。不要执行以下操作：

```text
git reset --hard
git checkout -- .
```

不要回滚不属于当前任务的修改。开始工作前先运行：

```powershell
git status --short
git diff
```

当前曾观察到除宗门文件外，以下文件也有改动：

```text
app/build.gradle
gradle/wrapper/gradle-wrapper.properties
app/src/main/AndroidManifest.xml
app/src/main/java/com/junklord/world/ui/MainActivity.kt
app/src/main/res/layout/activity_main.xml
app/src/main/res/menu/bottom_nav_menu.xml
```

不要假设这些改动由同一个 AI 产生，也不要为了整理提交而擅自恢复。

服务端工作区曾有无关的 `package.json` 和 `package-lock.json` 变化。除非确实需要，不要把它们混进宗门手机端工作。

## 10. 明确禁止事项

- 不要把服务端地址硬编码成 `127.0.0.1` 或某个局域网 IP。
- 继续复用 `JunklordApiService.BASE_URL` 和现有 `ApiClient`。
- 不要让手机端直接发放会战奖励或修改玩家资源。
- 不要绕过服务端邮件奖励链路。
- 不要为了“看起来完整”在客户端伪造服务端不存在的任命长老或转让宗主功能。
- 不要修改手环端，除非确认邮件领奖链路不支持服务端实际发放的奖励字段。
- 不要只给分析结论。如果发现明确代码问题，应修复并重新运行构建。

## 11. 下一个 AI 的输出要求

完成检查后，请按以下顺序报告：

1. 发现的问题，按严重程度排序，并给出文件和行号。
2. 已修复的问题和行为变化。
3. 执行过的验证命令及结果。
4. 仍然只能通过实机或多账号环境确认的事项。
5. 是否可以明确判断“代码层面已完成，只剩人工测试”。

如果没有发现问题，也要明确说明检查范围、剩余测试缺口和残余风险，不要只说“没有问题”。
