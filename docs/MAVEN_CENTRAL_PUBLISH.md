# SDK 发布到 Maven Central 指南

> **目标**: 让用户可以直接通过 Maven 坐标引入 SDK，无需手动下载 JAR  
> **重要性**: ⭐⭐⭐⭐⭐ (极大提升用户体验)  
> **预计耗时**: 首次 4-6 小时，后续 10 分钟

---

## 一、为什么要发布到 Maven Central？

### ❌ 当前问题

用户无法直接引入依赖：
```xml
<!-- ❌ 当前：无法直接使用 -->
<dependency>
    <groupId>com.retry.platform</groupId>
    <artifactId>retry-client-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
<!-- 报错：Could not find artifact -->
```

用户需要：
1. 手动下载 JAR 包
2. 或者配置私有 Maven 仓库
3. 或者本地 `mvn install`

### ✅ 发布后效果

```xml
<!-- ✅ 发布后：直接引入，Maven 自动下载 -->
<dependency>
    <groupId>com.retry.platform</groupId>
    <artifactId>retry-client-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

**收益**:
- ✅ 用户体验提升 100%
- ✅ 降低接入门槛
- ✅ 增加项目可信度
- ✅ 提升开源项目影响力

---

## 二、发布前准备工作

### 1. 注册 Sonatype OSSRH 账号

#### Step 1: 创建 Jira 账号

访问: https://issues.sonatype.org/secure/Signup!default.jspa

```
Username: your-username
Email: your-email@example.com
Full Name: Your Name
```

#### Step 2: 创建 Issue 申请 GroupId

登录后，创建一个 "New Project" issue：

```
Project: Community Support - Open Source Project Repository Hosting (OSSRH)
Issue Type: New Project

Summary: Request for com.retry.platform groupId
Description:
  I would like to publish ElecCloud (a distributed retry platform) 
  to Maven Central Repository.
  
  Project URL: https://github.com/your-username/eleccloud
  SCM URL: https://github.com/your-username/eleccloud.git
  
Group Id: com.retry.platform
Already have a Github.com username: yes (your-github-username)
```

#### Step 3: 验证域名所有权

**选项1: 如果你有域名 retry.platform (推荐)**
- 添加 TXT 记录验证所有权
- 或者在域名根目录创建验证文件

**选项2: 如果没有域名 (使用 GitHub)**
- 使用 GroupId: `io.github.your-username`
- 或者: `com.github.your-username`
- Sonatype 会自动验证你的 GitHub 仓库

**建议**: 使用 `io.github.your-username` 更简单：
```xml
<groupId>io.github.yourusername</groupId>
<artifactId>eleccloud-sdk</artifactId>
```

#### Step 4: 等待审批

通常 1-2 个工作日，Sonatype 会回复你的 Issue：
```
✅ Configuration has been prepared, now you can:
   Deploy snapshot artifacts
   Deploy release artifacts
```

---

### 2. 配置 GPG 签名

Maven Central 要求所有发布的构件必须使用 GPG 签名。

#### Step 1: 安装 GPG

**Windows**:
```powershell
# 下载安装 Gpg4win
# https://www.gpg4win.org/download.html

# 验证安装
gpg --version
```

**Mac**:
```bash
brew install gnupg
```

**Linux**:
```bash
sudo apt-get install gnupg
```

#### Step 2: 生成 GPG 密钥对

```bash
gpg --gen-key
```

交互式输入：
```
Real name: Your Name
Email: your-email@example.com
Passphrase: your-secure-passphrase (请记住这个密码！)
```

#### Step 3: 查看密钥

```bash
gpg --list-keys

# 输出示例:
# pub   rsa3072 2024-08-31 [SC]
#       1234567890ABCDEF1234567890ABCDEF12345678
# uid           Your Name <your-email@example.com>
```

记住这个密钥ID: `1234567890ABCDEF1234567890ABCDEF12345678`

#### Step 4: 上传公钥到密钥服务器

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys 1234567890ABCDEF1234567890ABCDEF12345678

# 或者使用其他服务器
gpg --keyserver keys.openpgp.org --send-keys 1234567890ABCDEF1234567890ABCDEF12345678
```

**验证上传成功**:
```bash
gpg --keyserver keyserver.ubuntu.com --recv-keys 1234567890ABCDEF1234567890ABCDEF12345678
```

---

### 3. 配置 Maven settings.xml

编辑 `~/.m2/settings.xml` (如果不存在则创建)：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              http://maven.apache.org/xsd/settings-1.0.0.xsd">
    
    <!-- Sonatype OSSRH 认证信息 -->
    <servers>
        <server>
            <id>ossrh</id>
            <username>your-sonatype-username</username>
            <password>your-sonatype-password</password>
        </server>
    </servers>
    
    <!-- GPG 签名配置 -->
    <profiles>
        <profile>
            <id>ossrh</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <gpg.executable>gpg</gpg.executable>
                <gpg.keyname>1234567890ABCDEF1234567890ABCDEF12345678</gpg.keyname>
                <gpg.passphrase>your-gpg-passphrase</gpg.passphrase>
            </properties>
        </profile>
    </profiles>
</settings>
```

**⚠️ 安全提示**: 
- 不要将 `settings.xml` 提交到 Git
- 生产环境使用 CI/CD 环境变量

---

## 三、配置项目 POM

### 1. 父 POM 配置 (pom.xml)

在项目根目录的 `pom.xml` 中添加：

```xml
<project>
    <!-- 基本信息 -->
    <groupId>io.github.yourusername</groupId>
    <artifactId>eleccloud-parent</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    
    <name>ElecCloud Distributed Retry Platform</name>
    <description>A high-performance distributed retry platform with local state machine</description>
    <url>https://github.com/yourusername/eleccloud</url>
    
    <!-- 开源协议 -->
    <licenses>
        <license>
            <name>Apache License, Version 2.0</name>
            <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
            <distribution>repo</distribution>
        </license>
    </licenses>
    
    <!-- 开发者信息 -->
    <developers>
        <developer>
            <id>yourusername</id>
            <name>Your Name</name>
            <email>your-email@example.com</email>
            <url>https://github.com/yourusername</url>
        </developer>
    </developers>
    
    <!-- SCM 信息 -->
    <scm>
        <connection>scm:git:git://github.com/yourusername/eleccloud.git</connection>
        <developerConnection>scm:git:ssh://github.com/yourusername/eleccloud.git</developerConnection>
        <url>https://github.com/yourusername/eleccloud/tree/main</url>
    </scm>
    
    <!-- 发布配置 -->
    <distributionManagement>
        <snapshotRepository>
            <id>ossrh</id>
            <url>https://s01.oss.sonatype.org/content/repositories/snapshots</url>
        </snapshotRepository>
        <repository>
            <id>ossrh</id>
            <url>https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/</url>
        </repository>
    </distributionManagement>
    
    <build>
        <plugins>
            <!-- Source -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-source-plugin</artifactId>
                <version>3.2.1</version>
                <executions>
                    <execution>
                        <id>attach-sources</id>
                        <goals>
                            <goal>jar-no-fork</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            
            <!-- Javadoc -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-javadoc-plugin</artifactId>
                <version>3.4.1</version>
                <configuration>
                    <encoding>UTF-8</encoding>
                    <charset>UTF-8</charset>
                    <docencoding>UTF-8</docencoding>
                    <!-- Java 8+ 需要禁用严格检查 -->
                    <doclint>none</doclint>
                </configuration>
                <executions>
                    <execution>
                        <id>attach-javadocs</id>
                        <goals>
                            <goal>jar</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            
            <!-- GPG 签名 -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-gpg-plugin</artifactId>
                <version>3.0.1</version>
                <executions>
                    <execution>
                        <id>sign-artifacts</id>
                        <phase>verify</phase>
                        <goals>
                            <goal>sign</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            
            <!-- Nexus Staging -->
            <plugin>
                <groupId>org.sonatype.plugins</groupId>
                <artifactId>nexus-staging-maven-plugin</artifactId>
                <version>1.6.13</version>
                <extensions>true</extensions>
                <configuration>
                    <serverId>ossrh</serverId>
                    <nexusUrl>https://s01.oss.sonatype.org/</nexusUrl>
                    <autoReleaseAfterClose>true</autoReleaseAfterClose>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 四、发布流程

### 1. 发布 SNAPSHOT 版本（测试）

SNAPSHOT 版本用于测试，可以随时覆盖。

```bash
# 1. 确保版本号是 SNAPSHOT
# pom.xml: <version>1.0.0-SNAPSHOT</version>

# 2. 编译打包
mvn clean package

# 3. 部署到 OSSRH Snapshots 仓库
mvn clean deploy

# 4. 验证
# 访问: https://s01.oss.sonatype.org/content/repositories/snapshots/io/github/yourusername/eleccloud-sdk/
```

**用户使用 SNAPSHOT**:
```xml
<repositories>
    <repository>
        <id>ossrh-snapshots</id>
        <url>https://s01.oss.sonatype.org/content/repositories/snapshots</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>

<dependency>
    <groupId>io.github.yourusername</groupId>
    <artifactId>eleccloud-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

### 2. 发布 RELEASE 版本（正式）

#### Step 1: 准备发布

```bash
# 1. 确保代码已提交
git status

# 2. 确保版本号是正式版本（非 SNAPSHOT）
# pom.xml: <version>1.0.0</version>

# 3. 确保所有测试通过
mvn clean test

# 4. 更新 CHANGELOG.md
# 记录本次发布的改动
```

#### Step 2: 执行发布

```bash
# 方式1: 使用 Maven Release Plugin (推荐)
mvn release:prepare
mvn release:perform

# 方式2: 手动发布
mvn clean deploy -P release
```

**发布过程**:
1. 编译项目
2. 运行测试
3. 生成 source jar
4. 生成 javadoc jar
5. GPG 签名所有文件
6. 上传到 OSSRH Staging 仓库

#### Step 3: 登录 Nexus 确认并发布

1. 访问: https://s01.oss.sonatype.org/
2. 登录你的 Sonatype 账号
3. 左侧菜单: **Staging Repositories**
4. 找到你的仓库 (状态: Open)
5. 选中后点击 **Close** (会自动验证)
6. 验证通过后点击 **Release**

**验证内容**:
- ✅ POM 文件完整性
- ✅ 包含 source jar
- ✅ 包含 javadoc jar
- ✅ 所有文件都有 GPG 签名
- ✅ GroupId 已授权

#### Step 4: 等待同步到 Maven Central

- **同步时间**: 2-4 小时
- **搜索可见**: 24 小时内

**验证发布成功**:
```bash
# 方式1: 搜索 Maven Central
# https://search.maven.org/

# 方式2: 直接访问
# https://repo1.maven.org/maven2/io/github/yourusername/eleccloud-sdk/1.0.0/

# 方式3: 测试引入
mvn dependency:get \
  -Dartifact=io.github.yourusername:eleccloud-sdk:1.0.0
```

---

## 五、后续版本发布

### 快速发布流程

```bash
# 1. 修改版本号
mvn versions:set -DnewVersion=1.0.1

# 2. 提交代码
git add .
git commit -m "chore: release version 1.0.1"
git tag v1.0.1
git push origin main
git push origin v1.0.1

# 3. 发布
mvn clean deploy -P release

# 4. Nexus 确认发布
# 访问 https://s01.oss.sonatype.org/
# Close → Release
```

---

## 六、CI/CD 自动化发布

### GitHub Actions 配置

创建 `.github/workflows/maven-publish.yml`:

```yaml
name: Publish to Maven Central

on:
  release:
    types: [created]

jobs:
  publish:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 8
        uses: actions/setup-java@v3
        with:
          java-version: '8'
          distribution: 'temurin'
          server-id: ossrh
          server-username: MAVEN_USERNAME
          server-password: MAVEN_PASSWORD
          gpg-private-key: ${{ secrets.GPG_PRIVATE_KEY }}
          gpg-passphrase: MAVEN_GPG_PASSPHRASE
      
      - name: Publish to Maven Central
        run: mvn clean deploy -P release
        env:
          MAVEN_USERNAME: ${{ secrets.OSSRH_USERNAME }}
          MAVEN_PASSWORD: ${{ secrets.OSSRH_PASSWORD }}
          MAVEN_GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}
```

### 配置 GitHub Secrets

在 GitHub 仓库设置中添加：

```
Settings → Secrets and variables → Actions → New repository secret

OSSRH_USERNAME: your-sonatype-username
OSSRH_PASSWORD: your-sonatype-password
GPG_PASSPHRASE: your-gpg-passphrase
GPG_PRIVATE_KEY: (导出的私钥)
```

**导出 GPG 私钥**:
```bash
gpg --armor --export-secret-keys your-email@example.com > private-key.asc
# 复制 private-key.asc 的内容到 GitHub Secret
```

---

## 七、常见问题

### Q1: 401 Unauthorized

**原因**: `settings.xml` 中的用户名密码错误

**解决**:
```bash
# 验证登录
curl -u your-username:your-password https://s01.oss.sonatype.org/service/local/authentication/login
```

### Q2: gpg: signing failed: Inappropriate ioctl for device

**原因**: GPG 无法获取密码输入

**解决**:
```bash
export GPG_TTY=$(tty)
# 或在 settings.xml 中配置 <gpg.passphrase>
```

### Q3: No public key found

**原因**: 公钥未上传到密钥服务器

**解决**:
```bash
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

### Q4: 404 Repository Not Found

**原因**: GroupId 未授权或 Issue 未处理完成

**解决**: 等待 Sonatype Issue 审批通过

### Q5: Javadoc 生成失败

**原因**: Javadoc 格式不规范

**解决**:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <configuration>
        <doclint>none</doclint> <!-- 禁用严格检查 -->
    </configuration>
</plugin>
```

---

## 八、发布后更新文档

### 1. 更新 README.md

```markdown
## 快速开始

### Maven
\```xml
<dependency>
    <groupId>io.github.yourusername</groupId>
    <artifactId>eleccloud-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
\```

### Gradle
\```groovy
implementation 'io.github.yourusername:eleccloud-sdk:1.0.0'
\```
```

### 2. 更新 SDK_GUIDE.md

```markdown
## Step 1: 添加依赖

\```xml
<dependency>
    <groupId>io.github.yourusername</groupId>
    <artifactId>eleccloud-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
\```

**最新版本**: [![Maven Central](https://img.shields.io/maven-central/v/io.github.yourusername/eleccloud-sdk.svg)](https://search.maven.org/artifact/io.github.yourusername/eleccloud-sdk)
```

### 3. 添加版本徽章

在 README.md 顶部添加：

```markdown
[![Maven Central](https://img.shields.io/maven-central/v/io.github.yourusername/eleccloud-sdk.svg)](https://search.maven.org/artifact/io.github.yourusername/eleccloud-sdk)
[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](LICENSE)
```

---

## 九、总结

### ✅ 完成清单

- [ ] 注册 Sonatype OSSRH 账号
- [ ] 创建 Jira Issue 申请 GroupId
- [ ] 配置 GPG 签名
- [ ] 配置 Maven settings.xml
- [ ] 更新项目 POM 配置
- [ ] 发布 SNAPSHOT 测试
- [ ] 发布 RELEASE 正式版
- [ ] 验证 Maven Central 可用
- [ ] 更新文档和徽章
- [ ] (可选) 配置 CI/CD 自动发布

### 📊 预计耗时

| 步骤 | 首次 | 后续 |
|------|------|------|
| 注册审批 | 1-2天 | - |
| 配置环境 | 2-3小时 | - |
| 首次发布 | 1-2小时 | 10分钟 |
| 同步等待 | 2-4小时 | 2-4小时 |
| **总计** | **4-6小时+审批** | **10分钟+同步** |

### 🎯 发布后收益

- ✅ 用户可直接引入依赖
- ✅ 接入时间从 10分钟 → **1分钟**
- ✅ 提升项目专业度和可信度
- ✅ 出现在 Maven Central 搜索结果
- ✅ 版本管理更规范

---

**参考资料**:
- [OSSRH Guide](https://central.sonatype.org/publish/publish-guide/)
- [Maven Central Requirements](https://central.sonatype.org/publish/requirements/)
- [GPG Guide](https://central.sonatype.org/publish/requirements/gpg/)
