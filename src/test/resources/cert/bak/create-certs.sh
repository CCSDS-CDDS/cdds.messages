#!/usr/bin/env sh
set -e

echo "Generating CDDS mTLS certificates in $(pwd)"
echo

# ----------------------------
# Configuration
# ----------------------------
CA_NAME="cdds-ca"
PROVIDER_NAME="cdds-provider"
USER_NAME="cdds-user"
SAN_NAME="theSpacecraft"

CA_DAYS=3650
LEAF_DAYS=825

# ----------------------------
# CA
# ----------------------------
echo "==> Creating CA key and certificate"

openssl genrsa -out ${CA_NAME}.key 4096

openssl req -x509 -new -nodes \
  -key ${CA_NAME}.key \
  -sha256 \
  -days ${CA_DAYS} \
  -out ${CA_NAME}.pem \
  -subj "/C=EU/O=CDDS/OU=PKI/CN=CDDS-Root-CA"

# ----------------------------
# Provider config
# ----------------------------
cat > ${PROVIDER_NAME}.cnf <<EOF
[ req ]
default_bits       = 4096
prompt             = no
default_md         = sha256
distinguished_name = dn
req_extensions     = req_ext

[ dn ]
C  = EU
O  = CDDS
OU = Provider
CN = ${SAN_NAME}

[ req_ext ]
subjectAltName = @alt_names
extendedKeyUsage = serverAuth

[ alt_names ]
DNS.1 = ${SAN_NAME}
DNS.2 = localhost
IP.1  = 127.0.0.1
EOF

# ----------------------------
# Provider cert
# ----------------------------
echo "==> Creating provider (server) key and certificate"

openssl genrsa -out ${PROVIDER_NAME}.key 4096

openssl req -new \
  -key ${PROVIDER_NAME}.key \
  -out ${PROVIDER_NAME}.csr \
  -config ${PROVIDER_NAME}.cnf

openssl x509 -req \
  -in ${PROVIDER_NAME}.csr \
  -CA ${CA_NAME}.pem \
  -CAkey ${CA_NAME}.key \
  -CAcreateserial \
  -out ${PROVIDER_NAME}.pem \
  -days ${LEAF_DAYS} \
  -sha256 \
  -extensions req_ext \
  -extfile ${PROVIDER_NAME}.cnf

# ----------------------------
# User config
# ----------------------------
cat > ${USER_NAME}.cnf <<EOF
[ req ]
default_bits       = 4096
prompt             = no
default_md         = sha256
distinguished_name = dn
req_extensions     = req_ext

[ dn ]
C  = EU
O  = CDDS
OU = User
CN = ${SAN_NAME}

[ req_ext ]
subjectAltName = @alt_names
extendedKeyUsage = clientAuth

[ alt_names ]
DNS.1 = ${SAN_NAME}
DNS.2 = localhost
IP.1  = 127.0.0.1
EOF

# ----------------------------
# User cert
# ----------------------------
echo "==> Creating user (client) key and certificate"

openssl genrsa -out ${USER_NAME}.key 4096

openssl req -new \
  -key ${USER_NAME}.key \
  -out ${USER_NAME}.csr \
  -config ${USER_NAME}.cnf

openssl x509 -req \
  -in ${USER_NAME}.csr \
  -CA ${CA_NAME}.pem \
  -CAkey ${CA_NAME}.key \
  -CAcreateserial \
  -out ${USER_NAME}.pem \
  -days ${LEAF_DAYS} \
  -sha256 \
  -extensions req_ext \
  -extfile ${USER_NAME}.cnf

# ----------------------------
# Cleanup
# ----------------------------
rm -f *.c*
