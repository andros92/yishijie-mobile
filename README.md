# 异世界传说 手机端（Android）

参照《对决 / 垃圾佬伴侣》手机端改造：包名 `com.yishijie.chuanshuo`，
服务器地址 `https://yxc.shatangju.wang`（与开箱/对决一致）。

## 功能

- 手环桥接：小米穿戴 SDK，`__hs__` 握手 + `tag:"game"` 消息协议（与手环端一致）
- 账号：设备指纹注册/登录（手环指纹优先，手机指纹兜底）
- 存档：上传/下载到服务器，或与手环互传（整包 JSON）
- 交易所：挂单 / 购买（平台收 10% 手续费）/ 撤单 / 成交记录
- 充值：创建订单 + 支付回调（测试阶段用管理密钥确认到账）
- 公告

## 构建

```bash
# 需要 JDK 8~15（Gradle 6.7.1），Android SDK 30
gradlew.bat assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

签名：`release.jks` 由手环证书（`../survival/sign/android/release.jks`）复制而来，
APK 证书指纹与手环 rpk 完全一致（SHA256 `EB:A0:05:63…`）。
`keystore.properties` 已被 gitignore，需自行放置：

```properties
release.store.file=../release.jks
release.store.password=yishijie
release.key.alias=yishijie
release.key.password=yishijie
```

## 服务端接口（/api/yishijie）

见 `../yishijie-server/README.md`。
