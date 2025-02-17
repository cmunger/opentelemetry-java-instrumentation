/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;

public final class JmsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jms-1.1";

  private static final Instrumenter<MessageWithDestination, Void> PRODUCER_INSTRUMENTER =
      buildProducerInstrumenter();
  private static final Instrumenter<MessageWithDestination, Void> CONSUMER_INSTRUMENTER =
      buildConsumerInstrumenter();
  private static final Instrumenter<MessageWithDestination, Void> LISTENER_INSTRUMENTER =
      buildListenerInstrumenter();

  private static Instrumenter<MessageWithDestination, Void> buildProducerInstrumenter() {
    JmsMessageAttributesGetter getter = JmsMessageAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.SEND;

    return Instrumenter.<MessageWithDestination, Void>builder(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            MessagingSpanNameExtractor.create(getter, operation))
        .addAttributesExtractor(buildMessagingAttributesExtractor(getter, operation))
        .buildProducerInstrumenter(MessagePropertySetter.INSTANCE);
  }

  private static Instrumenter<MessageWithDestination, Void> buildConsumerInstrumenter() {
    JmsMessageAttributesGetter getter = JmsMessageAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.RECEIVE;

    // MessageConsumer does not do context propagation
    return Instrumenter.<MessageWithDestination, Void>builder(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            MessagingSpanNameExtractor.create(getter, operation))
        .addAttributesExtractor(buildMessagingAttributesExtractor(getter, operation))
        .setEnabled(ExperimentalConfig.get().messagingReceiveInstrumentationEnabled())
        .addSpanLinksExtractor(
            new PropagatorBasedSpanLinksExtractor<>(
                GlobalOpenTelemetry.getPropagators().getTextMapPropagator(),
                MessagePropertyGetter.INSTANCE))
        .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  private static Instrumenter<MessageWithDestination, Void> buildListenerInstrumenter() {
    JmsMessageAttributesGetter getter = JmsMessageAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.PROCESS;

    return Instrumenter.<MessageWithDestination, Void>builder(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            MessagingSpanNameExtractor.create(getter, operation))
        .addAttributesExtractor(buildMessagingAttributesExtractor(getter, operation))
        .buildConsumerInstrumenter(MessagePropertyGetter.INSTANCE);
  }

  private static MessagingAttributesExtractor<MessageWithDestination, Void>
      buildMessagingAttributesExtractor(
          MessagingAttributesGetter<MessageWithDestination, Void> getter,
          MessageOperation operation) {
    return MessagingAttributesExtractor.builder(getter, operation)
        .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
        .build();
  }

  public static Instrumenter<MessageWithDestination, Void> producerInstrumenter() {
    return PRODUCER_INSTRUMENTER;
  }

  public static Instrumenter<MessageWithDestination, Void> consumerInstrumenter() {
    return CONSUMER_INSTRUMENTER;
  }

  public static Instrumenter<MessageWithDestination, Void> listenerInstrumenter() {
    return LISTENER_INSTRUMENTER;
  }

  private JmsSingletons() {}
}
