# SuperWaking

> AI 生成的项目文档，结论可能过时；以实测为准。

强制清醒闹钟：**振动为主、必须完成自我暗示任务才能关闭**。
目标设备：Honor 200（MagicOS 9 / Android 15），使用场景：学校宿舍，不能出声。

## 1. 要解决的问题

1. 睡梦中无意识关闹钟（一划、一按就没了）。
2. 同类 App 在国产 ROM 上"静音/震动"经常失效——根源通常是它们依赖**通知渠道震动**或**铃声音量**，而不是直接驱动马达。
3. 宿舍环境不能用声音，只能靠振动，且不能被音量键静音。

## 2. 技术约束（Android 15 + MagicOS 9）

| 约束 | 影响 | 应对 |
| --- | --- | --- |
| 精确闹钟需 `SCHEDULE_EXACT_ALARM` | 不授权则被 Doze 延后几分钟 | 引导页申请 + `AlarmManager.setAlarmClock()` |
| Android 14+ 全屏 Intent 需授权 | 锁屏时不弹界面 | `canUseFullScreenIntent()` 检测 + 跳设置 |
| 后台启动 Activity 受限 | 广播无法直接拉起界面 | 走 full-screen intent；备选高优先级通知 |
| MagicOS 应用启动管理 | 夜间被杀，闹钟不响 | 自启动/后台活动/电池优化白名单（手动） |
| 用户可强停、关机 | 任何第三方 App 都无法阻止 | 闹钟链 + 开机重排 + 系统闹钟兜底 |
| 振动可被系统关闭 | 静音且关闭振动时马达不工作 | 检测 `VIBRATE_ON`，提示；无法强行绕过 |

**核心差异化**：后台前台服务里用 `Vibrator` + `VibrationEffect.createWaveform()` **循环驱动马达**，
不依赖通知渠道、不依赖媒体音量，音量键对它无效。这是自研相对现成 App 的主要理由 [推测，需真机验证]。
Honor 系统设置里仍可关闭振动/强停进程，属物理边界。

## 3. 关闭门槛（v1 → v2）

- v1：输入预设句子（自我暗示语句），错字不放行；离开界面 15 秒后重新告警。
- v2：扫描贴在卫生间/书桌上的二维码（必须下床）。
- 备选：连续摇晃 N 次、长按 10 秒、算术题（易在半睡状态被机械完成，优先级低）。

闹钟链：T、T+2min、T+4min…… 连续排程，**只有完成任务才取消整条链**。
划掉通知、按返回、切后台都不会终止链条。

## 4. 开发方式候选

| 方案 | 做法 | 成本 | 最坏结果 |
| --- | --- | --- | --- |
| A. 云端 CI 构建 | 本机只写代码，GitHub Actions 出 APK，手机浏览器下载 | 首次 1–2 h；每轮构建 5–8 min | CI 配置踩坑；依赖网络与代理 |
| B. 本机装 SDK | JDK 17 + cmdline-tools + platform 35，约 5–6 GB | 首次 1–2 h；之后 30–60 s/次 | 占盘、环境变量折腾 |
| C. 零开发 | 先用 Sleep as Android / Alarmy 配好权限实测 | 0.5 h | 若震动仍失效，问题依旧 |

建议顺序：**先 C 做 3 晚对照实验 → 再 A 落地自研**。C 能验证"到底是权限问题还是 App 实现问题"，
避免花两周写出来发现 MagicOS 同样压制。

## 5. 红线

- 考试/重要事项期间**不作为唯一闹钟**，必须同时设系统闹钟。
- 不申请无障碍服务去锁死其他 App（高危、易被系统杀）。
- 不申请设备管理器/root。

## 6. 状态

- [x] git 初始化
- [x] 决策：本机便携工具链（`D:\Tools\android`：JDK 17.0.20.1 / Gradle 8.11.1 / SDK 35），不改系统环境变量
- [x] v1 实现：闹钟链、马达直驱前台服务、锁屏全屏界面、句子门槛、开机/改时间重排
- [x] 构建通过：`dist\superwaking-debug.apk`（0.8 MB，首次 2m18s）
- [ ] 真机验证（清单见 `docs/INSTALL.md` 第 4 节）
- [ ] v2：二维码门槛

## 7. 更新机制

- 版本号在 `version.properties`，`versionCode` 必须递增。
- 应用启动后每 24 小时静默检查一次 `api.github.com/repos/javaochen/superwaking/releases/latest`，也可点「检查更新」。
- 发现新版本 → 下载到 `cacheDir` → 经自定义 `ContentProvider` 交给系统安装器。系统仍会要求确认安装（Honor 可能多一次安全检测），这是平台边界，不是实现缺陷。
- 安装器读取 APK 需要「安装未知应用」权限，首次点更新会被引导去开。
- 签名固定为 `D:\Tools\android\keys\superwaking.jks`（SHA-256 `1D:DE:D5…`）。**该文件丢失后，已安装用户只能卸载重装**；备份在 `D:\Data\backup\superwaking-keys-20261105`，仍需另存一份到云盘或 U 盘。

## 8. 构建

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\works\app\superwaking\scripts\build.ps1
```

产物复制到 `dist\superwaking-debug.apk`。工具链全在 `D:\Tools\android`，删除该目录即可完全回滚。
