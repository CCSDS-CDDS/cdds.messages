#!/usr/bin/sh

echo "Generate proto documtation..."

protoc   --plugin=protoc-gen-doc=/home/holger/tools/protoc-gen-doc/protoc-gen-doc-1.5.1   --doc_out=target/doc   --doc_opt=html,index.html -I=src/main/protobuf  src/main/protobuf/telemetry.proto
