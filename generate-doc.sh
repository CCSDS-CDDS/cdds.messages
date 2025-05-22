#!/bin/sh

echo "Generate proto documtation..."

protoc  \
 --plugin=protoc-gen-doc=$(which protoc-gen-doc) \
 --doc_out=./target/generated-docs \
 --doc_opt=src/main/resources/cdds.tmpl,cdds.html \
 -I=src/main/protobuf \
 src/main/protobuf/telemetry.proto
