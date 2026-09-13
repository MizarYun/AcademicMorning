# 学术晨报 (Academic Morning)

**学术晨报APP** 是一款面向科研工作者的智能文献推送与阅读管理工具。

## 功能特性

- **每日晨报推送**：基于用户偏好的学科领域，每日自动推送最新学术论文（AlarmManager 精准闹钟 + 开机自恢复）
- **多源文献检索**：集成 Crossref、PubMed、arXiv 等学术数据源
- **期刊类型智能识别**：基于 Crossref 发表间隔自动识别周刊/半月刊/月刊/双月刊/半年刊，低频次期刊自动放宽检索窗口（双月刊 2 个月、半年刊 6 个月）
- **AI 摘要翻译**：支持大模型（DeepSeek / Kimi / 智谱）与机器翻译（腾讯云 TMT / 百度翻译）双通道，手动触发、引擎可选
- **期刊收藏管理**：自定义关注期刊，精准获取目标领域文献；收藏支持自定义分类标签
- **自定义日期范围检索**：支持指定期刊 + 起止日期（最长 1 个月）回溯抓取
- **阅读统计**：追踪阅读时长和习惯，量化科研投入
- **本地优先**：收藏永久保存，数据不出手机，零账号

## 技术栈

- Kotlin / Jetpack Compose (Material 3)
- Room 数据库（含版本迁移）
- WorkManager + AlarmManager 后台任务
- OkHttp 网络请求
- DataStore + Android Keystore 加密存储

## 版本信息

- 当前版本：**Beta 2.5**
- 发布日期：2026-09-13
- 完整更新记录见 [CHANGELOG.md](CHANGELOG.md)

## 下载安装

在 [Releases](https://github.com/MizarYun/AcademicMorning/releases) 页面下载最新 APK 安装包。

## 开源协议

本项目源代码开放浏览，供学习交流使用。
