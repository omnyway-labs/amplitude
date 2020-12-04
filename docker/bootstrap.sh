#!/bin/bash -xe

CLOJURE_TOOLS_VERSION=1.10.1.536
NODE_VERSION=v14.5.0
AMPLIFY_VERSION=4.24.2
export DEBIAN_FRONTEND=noninteractive

apt-get update
apt-get install -yy  --no-install-recommends curl gnupg procps git wget make git openssh-client bzip2 xz-utils unzip


curl -O https://download.clojure.org/install/linux-install-${CLOJURE_TOOLS_VERSION}.sh
chmod +x linux-install-${CLOJURE_TOOLS_VERSION}.sh
./linux-install-${CLOJURE_TOOLS_VERSION}.sh

wget https://nodejs.org/download/release/latest-v14.x/node-${NODE_VERSION}-linux-x64.tar.xz
du -sh node-${NODE_VERSION}-linux-x64.tar.xz
tar -C /usr/local --strip-components 1 -xf node-${NODE_VERSION}-linux-x64.tar.xz
node --version
npm install -g shadow-cljs
npm install -g @aws-amplify/cli@${AMPLIFY_VERSION}
npm install -g sass

curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add -
echo "deb https://dl.yarnpkg.com/debian/ stable main" | tee /etc/apt/sources.list.d/yarn.list
apt-get update
apt-get install --no-install-recommends -yy yarn

bash <(curl -s https://raw.githubusercontent.com/borkdude/jet/master/install)

curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
./aws/install
rm -f awscliv2.zip

mkdir -p ~/.clojure
mv /build/deps.edn ~/.clojure/deps.edn
mv /build/vulcan /usr/local/bin/vulcan
chmod +x /usr/local/bin/vulcan

wget https://github.com/aktau/github-release/releases/download/v0.7.2/linux-amd64-github-release.tar.bz2
tar jxvf linux-amd64-github-release.tar.bz2
mv bin/linux/amd64/github-release /usr/local/bin/github-release
chmod +x /usr/local/bin/github-release
rm -f linux-amd64-github-release.tar.bz2


# cleanup
npm cache clean --force
apt-get clean
apt-get autoremove
apt-get remove -yy gcc
rm -rf /build/.git
