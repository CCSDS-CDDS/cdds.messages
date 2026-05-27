#!/bin/sh

mvn verify $@ -Dprotoc-gen-doc=/Users/Shared/tools/protoc-gen-doc/1.5.2/protoc-gen-doc \
    -Dprotoc-gen-doc=/Users/Shared/tools/protoc-gen-doc/1.5.2/protoc-gen-doc \
    -Dsort-source=:sort=source -DskipTests
