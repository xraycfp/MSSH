# MSSH

移动端 SSH 客户端，基于 Kotlin Multiplatform Compose 开发。

## 技术栈

- **UI 框架**: Compose Multiplatform
- **SSH 库**: ConnectBot sshlib
- **终端模拟**: 自定义 Canvas 终端渲染
- **依赖注入**: Koin
- **数据库**: SQLDelight
- **导航**: Compose Navigation (KMP)
- **架构**: MVVM (ViewModel + StateFlow + Repository)

## 构建

```bash
./gradlew :composeApp:assembleDebug
```

## 功能

- [x] SSH 连接管理（增删改查）
- [x] 密码认证
- [x] 密钥认证
- [x] 终端交互
- [x] 多会话标签页
- [x] SSH 密钥管理（生成/导入/导出）
