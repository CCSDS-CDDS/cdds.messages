#!/bin/sh

echo "Generate proto documtation..."

protoc -Isrc/main/protobuf --plugin=protoc-gen-doc=../../protoc-gen-doc/bin/protoc-gen-doc --doc_out=target/generated-docs --doc_opt=src/main/resources/cdds.tmpl,cdds.html:dummy.proto:sort=source src/main/protobuf/*.proto
