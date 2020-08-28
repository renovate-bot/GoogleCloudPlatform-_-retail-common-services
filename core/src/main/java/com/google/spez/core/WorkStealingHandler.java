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

import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.spannerclient.Row;
import com.google.spez.core.SpannerToAvro.SchemaSet;
import io.opencensus.trace.Span;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkStealingHandler implements SpannerEventHandler {
  private static final Logger log = LoggerFactory.getLogger(WorkStealingHandler.class);

  private final ExecutorService workStealingPool = Executors.newWorkStealingPool();
  private final ListeningExecutorService forkJoinPool =
      MoreExecutors.listeningDecorator(workStealingPool);
  private final AtomicLong records = new AtomicLong(0);
  private final AtomicLong errors = new AtomicLong(0);
  private final AtomicLong published = new AtomicLong(0);
  private final AtomicReference<String> lastProcessedTimestamp = new AtomicReference("");
  private final Instant then = Instant.now();
  private final Runtime runtime = Runtime.getRuntime();
  private final NumberFormat formatter = NumberFormat.getInstance();

  private final SchemaSet schemaSet;
  private final EventPublisher publisher;
  private final MetadataExtractor extractor;

  /**
   * Constructor.
   *
   * @param schemaSet description
   * @param publisher description
   * @param extractor description
   */
  public WorkStealingHandler(
      SchemaSet schemaSet, EventPublisher publisher, MetadataExtractor extractor) {
    this.schemaSet = schemaSet;
    this.publisher = publisher;
    this.extractor = extractor;
  }

  /** log stats. */
  public void logStats() {
    Instant now = Instant.now();
    Duration d = Duration.between(then, now);
    long count = records.get();
    long err = errors.get();
    long pub = published.get();
    log.info(
        "Processed {} records [errors: {} / published: {}] over the past {}",
        formatter.format(count),
        err,
        pub,
        d);
    log.info("lastProcessedTimestamp: {}", lastProcessedTimestamp.get());
    log.info("forkJoinPool {}", workStealingPool);
    log.info(
        "Memory: {}free / {}tot / {}max",
        formatter.format(runtime.freeMemory()),
        formatter.format(runtime.totalMemory()),
        formatter.format(runtime.maxMemory()));
  }

  public Boolean publishRecord(Row s, Span parent) {
    // TODO(xjdr): Throw if empty optional
    log.debug("Processing Record");
    String timestamp = s.getTimestamp(schemaSet.tsColName()).toString();
    lastProcessedTimestamp.set(timestamp);

    ListenableFuture<Optional<ByteString>> record =
        forkJoinPool.submit(() -> SpannerToAvro.MakeRecord(schemaSet, s));

    log.debug("Record Processed, getting ready to publish");

    var metadata = extractor.extract(s);

    AsyncFunction<Optional<ByteString>, String> pub =
        new AsyncFunction<Optional<ByteString>, String>() {
          @Override
          public ListenableFuture<String> apply(Optional<ByteString> record) {
            return publisher.publish(record.get(), metadata, parent);
          }
        };

    ListenableFuture<String> resp = Futures.transformAsync(record, pub, forkJoinPool);

    Futures.addCallback(
        resp,
        new FutureCallback<String>() {

          @Override
          public void onSuccess(String result) {
            // Once published, returns server-assigned message ids
            // (unique within the topic)
            log.debug(
                "Published message for timestamp '{}' with message id '{}'", timestamp, result);
            published.incrementAndGet();
          }

          @Override
          public void onFailure(Throwable t) {
            errors.incrementAndGet();
            log.error("Error Publishing Record: ", t);
          }
        },
        forkJoinPool);

    return Boolean.TRUE;
  }

  /**
   * Processes the event from the {@code SpannerTailer} in the form of a method callback.
   *
   * @param bucket Consistent Hash Table bucket id to aid in construction of method specific thread
   *     pooling strategies
   * @param timestamp the Cloud Spanner Commit Timestamp for the event
   */
  @Override
  public ListenableFuture<Boolean> process(int bucket, Row s, String timestamp, Span parent) {
    records.incrementAndGet();

    ListenableFuture<Boolean> x = forkJoinPool.submit(() -> publishRecord(s, parent));

    Futures.addCallback(
        x,
        new FutureCallback<Boolean>() {

          @Override
          public void onSuccess(Boolean result) {
            log.debug("Record Successfully Published");
          }

          @Override
          public void onFailure(Throwable t) {
            log.error("Unable to process record", t);
          }
        },
        forkJoinPool);

    return x;
  }
}
