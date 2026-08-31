#!/bin/bash

# ElecCloud Maven Central 发布准备脚本
# 此脚本帮助你快速配置发布到 Maven Central 的环境

set -e

echo "======================================"
echo "ElecCloud Maven Central 发布准备向导"
echo "======================================"
echo ""

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 1. 检查 GPG
echo "Step 1: 检查 GPG..."
if ! command -v gpg &> /dev/null; then
    echo -e "${RED}✗ GPG 未安装${NC}"
    echo "请安装 GPG:"
    echo "  Windows: https://www.gpg4win.org/download.html"
    echo "  Mac: brew install gnupg"
    echo "  Linux: sudo apt-get install gnupg"
    exit 1
else
    echo -e "${GREEN}✓ GPG 已安装${NC}"
    gpg --version | head -n 1
fi

# 2. 检查 GPG 密钥
echo ""
echo "Step 2: 检查 GPG 密钥..."
if gpg --list-keys | grep -q "uid"; then
    echo -e "${GREEN}✓ GPG 密钥已存在${NC}"
    gpg --list-keys
    echo ""
    read -p "是否使用现有密钥？(y/n): " use_existing
    if [ "$use_existing" != "y" ]; then
        echo "生成新密钥..."
        gpg --gen-key
    fi
else
    echo -e "${YELLOW}! 未找到 GPG 密钥，开始生成...${NC}"
    gpg --gen-key
fi

# 3. 获取密钥ID
echo ""
echo "Step 3: 获取密钥ID..."
KEY_ID=$(gpg --list-keys --keyid-format=long | grep "pub" | awk '{print $2}' | cut -d'/' -f2 | head -n 1)
echo -e "${GREEN}密钥ID: $KEY_ID${NC}"

# 4. 上传公钥
echo ""
echo "Step 4: 上传公钥到密钥服务器..."
read -p "是否上传公钥到 keyserver.ubuntu.com？(y/n): " upload_key
if [ "$upload_key" = "y" ]; then
    gpg --keyserver keyserver.ubuntu.com --send-keys $KEY_ID
    echo -e "${GREEN}✓ 公钥已上传${NC}"
fi

# 5. 配置 settings.xml
echo ""
echo "Step 5: 配置 Maven settings.xml..."
SETTINGS_FILE="$HOME/.m2/settings.xml"

if [ -f "$SETTINGS_FILE" ]; then
    echo -e "${YELLOW}! settings.xml 已存在${NC}"
    read -p "是否备份并更新？(y/n): " backup_settings
    if [ "$backup_settings" = "y" ]; then
        cp "$SETTINGS_FILE" "$SETTINGS_FILE.backup"
        echo "已备份到 $SETTINGS_FILE.backup"
    else
        echo "跳过 settings.xml 配置"
        exit 0
    fi
fi

# 获取用户输入
echo ""
echo "请输入 Sonatype OSSRH 账号信息:"
read -p "Username: " OSSRH_USERNAME
read -sp "Password: " OSSRH_PASSWORD
echo ""
read -sp "GPG Passphrase: " GPG_PASSPHRASE
echo ""

# 创建 settings.xml
mkdir -p "$HOME/.m2"
cat > "$SETTINGS_FILE" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              http://maven.apache.org/xsd/settings-1.0.0.xsd">
    
    <servers>
        <server>
            <id>ossrh</id>
            <username>$OSSRH_USERNAME</username>
            <password>$OSSRH_PASSWORD</password>
        </server>
    </servers>
    
    <profiles>
        <profile>
            <id>ossrh</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <gpg.executable>gpg</gpg.executable>
                <gpg.keyname>$KEY_ID</gpg.keyname>
                <gpg.passphrase>$GPG_PASSPHRASE</gpg.passphrase>
            </properties>
        </profile>
    </profiles>
</settings>
EOF

echo -e "${GREEN}✓ settings.xml 已创建${NC}"
echo "位置: $SETTINGS_FILE"

# 6. 测试配置
echo ""
echo "Step 6: 测试配置..."
echo "测试 Sonatype 登录..."
if curl -s -u "$OSSRH_USERNAME:$OSSRH_PASSWORD" https://s01.oss.sonatype.org/service/local/authentication/login | grep -q "username"; then
    echo -e "${GREEN}✓ Sonatype 认证成功${NC}"
else
    echo -e "${RED}✗ Sonatype 认证失败，请检查用户名密码${NC}"
fi

# 7. 下一步提示
echo ""
echo "======================================"
echo -e "${GREEN}✓ 配置完成！${NC}"
echo "======================================"
echo ""
echo "下一步操作:"
echo "1. 确保你的 Sonatype Jira Issue 已通过审批"
echo "2. 更新项目 pom.xml (参考 docs/MAVEN_CENTRAL_PUBLISH.md)"
echo "3. 发布 SNAPSHOT 测试:"
echo "   mvn clean deploy"
echo "4. 发布正式版本:"
echo "   mvn clean deploy -P release"
echo ""
echo "详细文档: docs/MAVEN_CENTRAL_PUBLISH.md"
echo ""
