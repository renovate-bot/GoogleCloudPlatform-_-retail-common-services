/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.spez.common;

import ch.qos.logback.classic.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("PMD.MoreThanOneLogger")
public class LoggerDumper {
  private static final Logger log = LoggerFactory.getLogger(LoggerDumper.class);

  public static void dump() {
    log.debug("Dumping all known Loggers");
    LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
    java.util.Iterator<ch.qos.logback.classic.Logger> it = lc.getLoggerList().iterator();
    while (it.hasNext()) {
      ch.qos.logback.classic.Logger thisLog = it.next();
      log.debug("name: {} status: {}", thisLog.getName(), thisLog.getLevel());
    }
  }
}
