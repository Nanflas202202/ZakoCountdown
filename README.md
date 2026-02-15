<div align="center">
    <img src="/ZCLogoFogo.png">
</div>



<div align="center">
    <h1>ZakoCountdown</h1>
<div align="center">
![GitHub repo size](https://img.shields.io/github/repo-size/nanflas202202/ZakoCountdown) 
![GitHub Repo stars](https://img.shields.io/github/stars/nanflas202202/ZakoCountdown) 
![GitHub all releases](https://img.shields.io/github/downloads/nanflas202202/ZakoCountdown/total) 
</div>
    <p>您最不靠谱的开源倒数日应用</p>
</div>

## 功能完成情况

---

## 适配平台

- [x] Android
- [ ] iOS
- [ ] Pad
- [ ] Windows
- [ ] Linux


## 核心功能

- [x] 用户界面
- [x] 创建及存储日程
- [x] 开屏弹窗提醒

## 功能

- [x] 用户界面相关
	- [x] 主题更换
	- [x] Dynamic Color适配
	- [x] 多主页布局切换
	- [x] 多导航方式切换 
	- [x] 壁纸自定义 
	- [ ] 横屏适配 
	- [ ] 日程卡片相关
		- [x] 日程卡片自定义颜色
		- [x] 日程卡片自定义背景
		- [x] 自定义倒计时布局 
		- [ ] 多主题/主页布局适配
	- [x] 日程集相关
		- [x] 日程集卡片自定义封面
		- [x] 日程集自定义标识色 
	- [x] 微件相关
		- [x] 微件自定义颜色
		- [x] 微件自定义背景
		- [x] 微件自定义样式
	- [ ] 开屏弹窗相关
		- [ ] 多主题适配
		- [ ] 自定义样式  
- [ ] 开屏弹窗相关
	- [x] 自定义弹出时长
	- [x] 自定义是否可以提前关闭
	- [x] 自定义要看多久后才可以关闭
	- [x] 自选弹出提示的应用
	- [ ] 自定义提示的日程‘
- [x] 通知相关
	- [x] 开机后自动弹出通知
	- [ ] 自定义通过通知提醒的条件
	- [ ] 自定义在通知中提醒的日程  
- [x] 日程集 
	- [x] 基础功能
	- [x] 批量添加/删除日程本中的日程
- [ ] 微件相关
	- [x] 在主页快速添加微件到桌面(*注：部分国产UI无效，且暂时无法自定义添加的类型*)  
	- [x] 自动更新数据
	- [x]  自定义在微件中显示的日程
- [x] 设置相关
	- [x] 提供到相关设置项的快捷入口
	- [x] 获取当前所需的权限的情况
	- [x] 国产UI的特殊权限动态提示（相关入口在非国产UI的环境下不显示） 
- [ ] 在线升级相关
	- [ ] 在线获取最新版本号
	- [ ] 在线更新
	- [ ] 后台自动更新  
- [ ] 分享相关
	- [x] 生成分享卡片
	- [ ] 分享日程
	- [ ] 分享app（短期内暂无加入的计划）  
- [ ] 导入导出日程
	- [ ] 导出配置文件
	- [ ] 批量导入导出日程
- [x] 添加日程到日历
- [ ] 等等

## 下载

可以通过右侧release进行下载或拉取代码到本地进行编译

## 声明

此项目（ZakoCountdown）是个人为了兴趣而开发，内含大量的AI生成代码和成吨的bug。本人并不擅长kotlin，我也是边学习Android平台上的开发边推进此项目的。许多bug也无力修复，这其实并不是一个很合格的app。

但无论如何，感谢您的使用

## 致谢

- [Google AI Studio](https://aistudio.google.com/)
- [Google Gemini](https://gemini.google.com)
- [DeepSeek](https://chat.deepseek.com)
- [Material Design](https://material.io)
- 等等

---

---

### 版本命名规则
以GenVer.BigVersion.SmallVersion-VersionCode的格式命名。规则如下

**GenVer**：代表这是第几代，即是否达成项目的预期功能

**BigVersion**：达成阶段性目标后大版本号+1

**SmallVersion**：随缘更新，更新则+1。如果版本号有跳跃则表示有达成某项预期功能，但过程中没有发布中间版本

**VersionCode**：表示这个版本的性质，格式如下
- *-debug:阶段性的早期版本，bug较多，调试用的
- *-nightly:热更新版本，一般相较于上一版本改动较少、发布间隔期短，bug修复为主
- *-prestable:较稳定的预览版
- *-*Final:阶段性的最终版，该版本号( *.*.X)在此版本后不会迭代
- *-stable：稳定版
