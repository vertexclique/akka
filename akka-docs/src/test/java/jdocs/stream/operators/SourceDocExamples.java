/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators;

// #imports
// #range-imports
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.actor.testkit.typed.javadsl.ManualTime;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.stream.javadsl.Source;
// #range-imports

// #actor-ref-imports
import akka.actor.ActorRef;
import akka.actor.Status.Success;
import akka.stream.OverflowStrategy;
import akka.stream.CompletionStrategy;
import akka.stream.javadsl.Sink;
import akka.testkit.TestProbe;
// #actor-ref-imports

import java.util.Arrays;
import java.util.Optional;

// #imports

public class SourceDocExamples {

  public static final TestKitJunitResource testKit = new TestKitJunitResource(ManualTime.config());

  public static void fromExample() {
    // #source-from-example
    final ActorSystem system = ActorSystem.create("SourceFromExample");

    Source<Integer, NotUsed> ints = Source.from(Arrays.asList(0, 1, 2, 3, 4, 5));
    ints.runForeach(System.out::println, system);

    String text =
        "Perfection is finally attained not when there is no longer more to add,"
            + "but when there is no longer anything to take away.";
    Source<String, NotUsed> words = Source.from(Arrays.asList(text.split("\\s")));
    words.runForeach(System.out::println, system);
    // #source-from-example
  }

  static void rangeExample() {

    final ActorSystem system = ActorSystem.create("Source");

    // #range

    Source<Integer, NotUsed> source = Source.range(1, 100);

    // #range

    // #range
    Source<Integer, NotUsed> sourceStepFive = Source.range(1, 100, 5);

    // #range

    // #range
    Source<Integer, NotUsed> sourceStepNegative = Source.range(100, 1, -1);
    // #range

    // #run-range
    source.runForeach(i -> System.out.println(i), system);
    // #run-range
  }

  static void actorRef() {
    // #actor-ref

    final ActorSystem system = ActorSystem.create();

    int bufferSize = 100;
    Source<Object, ActorRef> source = Source.actorRef(bufferSize, OverflowStrategy.dropHead());

    ActorRef actorRef = source.to(Sink.foreach(System.out::println)).run(system);
    actorRef.tell("hello", ActorRef.noSender());
    actorRef.tell("hello", ActorRef.noSender());

    // The stream completes successfully with the following message
    actorRef.tell(new Success(CompletionStrategy.draining()), ActorRef.noSender());
    // #actor-ref
  }

  static void actorRefWithBackpressure() {
    final TestProbe probe = null;

    // #actorRefWithBackpressure
    final ActorSystem system = ActorSystem.create();

    Source<Object, ActorRef> source =
        Source.actorRefWithBackpressure(
            "ack",
            o -> {
              if (o == "complete") return Optional.of(CompletionStrategy.draining());
              else return Optional.empty();
            },
            o -> Optional.empty());

    ActorRef actorRef = source.to(Sink.foreach(System.out::println)).run(system);
    probe.send(actorRef, "hello");
    probe.expectMsg("ack");
    probe.send(actorRef, "hello");
    probe.expectMsg("ack");

    // The stream completes successfully with the following message
    actorRef.tell("complete", ActorRef.noSender());
    // #actorRefWithBackpressure
  }
}
