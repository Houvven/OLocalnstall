# OLocalnstall

## 使用 GitHub Actions 构建签名 APK

### 准备签名密钥

使用已有的 Android 发布密钥库，或使用 JDK 的 `keytool` 创建一个新的密钥库。例如，下面的命令在用户目录创建密钥库，密钥别名为 `release`：

```sh
keytool -genkeypair -v -keystore "$HOME/release-key.jks" -alias release -keyalg RSA -keysize 2048 -validity 10000 -storetype JKS
```

按提示设置密钥库密码、密钥密码和证书信息。请妥善保存密钥库和密码，后续更新应用需要使用同一签名密钥。不要将密钥库或密码提交到仓库。

将密钥库编码为单行 Base64，复制完整输出。将以下示例中的路径替换为实际密钥库路径。

macOS / Linux：

```sh
base64 < "$HOME/release-key.jks" | tr -d '\r\n'
```

Windows PowerShell：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME/release-key.jks"))
```

### 配置仓库 Secrets

在 GitHub 仓库中打开 **Settings → Secrets and variables → Actions**，通过 **New repository secret** 添加以下四项：

| Secret | 值 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 密钥库文件的 Base64 编码结果 |
| `ANDROID_KEYSTORE_PASSWORD` | 密钥库密码 |
| `ANDROID_KEY_ALIAS` | 签名密钥的别名，例如上面的 `release` |
| `ANDROID_KEY_PASSWORD` | 签名密钥密码；如果与密钥库密码相同，填入相同的值 |

### 运行构建并下载

在仓库的 **Actions** 页面选择签名 release 构建工作流，点击 **Run workflow** 并选择要构建的分支。构建完成后，打开该次运行，在 **Artifacts** 中下载 `signed-release-apk`。

工作流从 `app/build/outputs/apk/release/app-release.apk` 上传签名 APK，下载的 artifact 压缩包中包含该 APK。
