#!/bin/bash

# ElecCloud — Maven Central Publishing Preparation Script
# Helps you quickly configure the environment for publishing to Maven Central.

set -e

echo "======================================"
echo "  ElecCloud Maven Central Setup Wizard"
echo "======================================"
echo ""

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Step 1: Check GPG
echo "Step 1: Checking GPG installation..."
if ! command -v gpg &> /dev/null; then
    echo -e "${RED}✗ GPG is not installed${NC}"
    echo "Install GPG:"
    echo "  Windows: https://www.gpg4win.org/download.html"
    echo "  Mac:     brew install gnupg"
    echo "  Linux:   sudo apt-get install gnupg"
    exit 1
else
    echo -e "${GREEN}✓ GPG is installed${NC}"
    gpg --version | head -n 1
fi

# Step 2: Check GPG keys
echo ""
echo "Step 2: Checking GPG keys..."
if gpg --list-keys | grep -q "uid"; then
    echo -e "${GREEN}✓ GPG key(s) found${NC}"
    gpg --list-keys
    echo ""
    read -p "Use existing key? (y/n): " use_existing
    if [ "$use_existing" != "y" ]; then
        echo "Generating a new key..."
        gpg --gen-key
    fi
else
    echo -e "${YELLOW}! No GPG key found — generating one now...${NC}"
    gpg --gen-key
fi

# Step 3: Get key ID
echo ""
echo "Step 3: Retrieving key ID..."
KEY_ID=$(gpg --list-keys --keyid-format=long | grep "pub" | awk '{print $2}' | cut -d'/' -f2 | head -n 1)
echo -e "${GREEN}Key ID: $KEY_ID${NC}"

# Step 4: Upload public key
echo ""
echo "Step 4: Uploading public key to key server..."
read -p "Upload public key to keyserver.ubuntu.com? (y/n): " upload_key
if [ "$upload_key" = "y" ]; then
    gpg --keyserver keyserver.ubuntu.com --send-keys $KEY_ID
    echo -e "${GREEN}✓ Public key uploaded${NC}"
fi

# Step 5: Configure settings.xml
echo ""
echo "Step 5: Configuring Maven settings.xml..."
SETTINGS_FILE="$HOME/.m2/settings.xml"

if [ -f "$SETTINGS_FILE" ]; then
    echo -e "${YELLOW}! settings.xml already exists${NC}"
    read -p "Backup and overwrite? (y/n): " backup_settings
    if [ "$backup_settings" = "y" ]; then
        cp "$SETTINGS_FILE" "$SETTINGS_FILE.backup"
        echo "Backed up to $SETTINGS_FILE.backup"
    else
        echo "Skipping settings.xml configuration."
        exit 0
    fi
fi

# Gather credentials
echo ""
echo "Enter your Sonatype OSSRH credentials:"
read -p "Username: " OSSRH_USERNAME
read -sp "Password: " OSSRH_PASSWORD
echo ""
read -sp "GPG Passphrase: " GPG_PASSPHRASE
echo ""

# Write settings.xml
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

echo -e "${GREEN}✓ settings.xml created${NC}"
echo "Location: $SETTINGS_FILE"

# Step 6: Test configuration
echo ""
echo "Step 6: Testing configuration..."
echo "Testing Sonatype login..."
if curl -s -u "$OSSRH_USERNAME:$OSSRH_PASSWORD" https://s01.oss.sonatype.org/service/local/authentication/login | grep -q "username"; then
    echo -e "${GREEN}✓ Sonatype authentication successful${NC}"
else
    echo -e "${RED}✗ Sonatype authentication failed — check your credentials${NC}"
fi

# Step 7: Next steps
echo ""
echo "======================================"
echo -e "${GREEN}✓ Setup complete!${NC}"
echo "======================================"
echo ""
echo "Next steps:"
echo "1. Ensure your Sonatype Jira Issue has been approved"
echo "2. Verify pom.xml contains required fields: groupId, name, description, url, licenses, developers, scm"
echo "3. Publish a SNAPSHOT to test:"
echo "   mvn clean deploy"
echo "4. Publish a release:"
echo "   mvn clean deploy -P release"
echo ""
echo "Reference: https://central.sonatype.org/publish/publish-guide/"
echo ""
