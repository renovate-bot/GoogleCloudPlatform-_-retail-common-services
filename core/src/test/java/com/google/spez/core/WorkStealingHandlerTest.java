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

package com.google.spez.core;

import com.google.cloud.Timestamp;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Value;
import com.google.spanner.v1.StructType;
import com.google.spanner.v1.Type;
import com.google.spanner.v1.TypeCode;
import com.google.spez.core.internal.BothanRow;
import com.google.spez.core.internal.Row;
import io.opencensus.trace.Span;
import org.apache.avro.SchemaBuilder;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkStealingHandlerTest implements WithAssertions {

  @Test
  void logStats() {}

  @Test
  void publishRecord() {}

  @Test
  void process(@Mock EventPublisher publisher, @Mock Span parent) throws Exception {
    var fields =
        ImmutableList.of(
            StructType.Field.newBuilder()
                .setName("uuid")
                .setType(Type.newBuilder().setCode(TypeCode.INT64).build())
                .build(),
            StructType.Field.newBuilder()
                .setName("timestamp")
                .setType(Type.newBuilder().setCode(TypeCode.TIMESTAMP).build())
                .build());
    var timestamp = Timestamp.ofTimeSecondsAndNanos(0, 0);
    var values =
        ImmutableList.of(
            Value.newBuilder().setStringValue("1234").build(),
            Value.newBuilder().setStringValue(timestamp.toString()).build());
    Row row = new BothanRow(new com.google.spannerclient.Row(fields, values));

    var avroSchema =
        SchemaBuilder.record("table")
            .namespace("namespace")
            .fields()
            .requiredBoolean("boolField")
            .endRecord();
    SchemaSet schemaSet = SchemaSet.create(avroSchema, ImmutableMap.of("key", "value"));
    var pubsub = new SpezConfig.PubSubConfig(null, "ledger-topic");
    var sink =
        new SpezConfig.SinkConfig(
            null, "sink-instance", "sink-database", "sink-table", "uuid", "timestamp", null);
    var config = new SpezConfig(null, pubsub, sink, null, null);
    var extractor = new MetadataExtractor(config);
    var handler = new WorkStealingHandler(schemaSet, sink, publisher, extractor);
    var future = handler.process(0, row, "", parent);
    future.get();
  }
}
