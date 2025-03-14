#!/bin/bash

apt update
apt -y upgrade
apt install wget -y
apt install curl -y
VERSION=$(curl --silent "https://api.github.com/repos/cloudflare/cfssl/releases/latest" | grep '"tag_name"' | sed -E 's/.*"([^"]+)".*/\1/')
VNUMBER=${VERSION#"v"}
wget https://github.com/cloudflare/cfssl/releases/download/${VERSION}/cfssl_${VNUMBER}_linux_amd64 -O cfssl
wget https://github.com/cloudflare/cfssl/releases/download/${VERSION}/cfssljson_${VNUMBER}_linux_amd64 -O cfssljson
chmod +x cfssl
chmod +x cfssljson
mv cfssl /usr/local/bin
mv cfssljson /usr/local/bin
cd CertificateAuthority/eng-certs/
cfssl ocspserve -port=8887 -responses=ocsp/ocspdump_subcas.txt  -loglevel=0 -address=0.0.0.0
