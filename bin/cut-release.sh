#!/bin/bash -ex
#
# Copyright 2020 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


TAG=$1
NEXT_TAG=$2

if [ -z $TAG ] || [ -z $NEXT_TAG ]; then
  echo "$0 tag next-tag"
  exit 1
fi


if ! git tag --contains $TAG > /dev/null 2>&1; then
  ./gradlew release -Prelease.useAutomaticVersion=true -Prelease.releaseVersion=$TAG -Prelease.newVersion=$NEXT_TAG-SNAPSHOT
fi

git checkout -b release-$TAG $TAG || git checkout release-$TAG

git push origin HEAD

git checkout main
