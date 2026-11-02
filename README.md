# ZeroTermux-plus

基于[ZeroTermux](https://github.com/hanxinhao000/ZeroTermux)（[Termux](https://github.com/termux/termux-app)的中文增强分支）的再增强版本，面向读屏用户与中文用户。

## 增强

- **无障碍修复**：侧栏菜单与工具箱新增屏幕悬浮按钮（原边缘滑动手势在TalkBack下无法触发），支持读屏聚焦与双击激活
- **界面翻译**：简体中文补齐15条漏翻，新增繁体中文全量翻译（1542条）
- **命令兼容**：内置termux-open、termux-clipboard、termux-wake-lock等12个常用命令
- **CI构建**：GitHub Actions自动构建并发布release（tag触发或手动触发）

## 计划

- 应用级API服务：检查更新、闹钟、WiFi状态、AI助手（自Cortex+移植）
- proot-distro预装与一键Ubuntu

## 安装

从[Releases](https://github.com/puraffert/ZeroTermux-plus/releases)下载APK安装。签名密钥与上游ZeroTermux一致，可直接覆盖升级。

## 构建

```bash
./gradlew assembleRelease
```

bootstrap压缩包由gradle任务`downloadBootstraps`自动从termux-packages获取。

## 许可

GPLv3（继承自Termux与ZeroTermux），详见[LICENSE](LICENSE.md)。
