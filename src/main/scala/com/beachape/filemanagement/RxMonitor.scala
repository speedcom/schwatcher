package com.beachape.filemanagement

import rx.subjects.PublishSubject
import java.nio.file.{WatchEvent, Path}
import akka.actor.{PoisonPill, ActorSystem}
import com.beachape.filemanagement.Messages.{UnRegisterCallback, RegisterBossyCallback}
import java.nio.file.WatchEvent.Modifier
import rx.lang.scala.Observable
import rx.lang.scala.JavaConversions.toScalaObservable

object RxMonitor {

  def apply(implicit actorSystem: ActorSystem = ActorSystem("actorSystem")): RxMonitor = new RxMonitor(actorSystem)
}

/**
 * The class that will be pushed out to subscribers of the RxMonitor's #observable
 */
sealed case class EventAtPath(path: Path, event: WatchEvent.Kind[Path])

/**
 * Reactive Extensions-based class
 *
 * If you want to have a Stream of some kind
 *
 * Created by Lloyd on 5/1/14.
 */
class RxMonitor(actorSystem: ActorSystem) {

  private val rxSubject = PublishSubject.create[EventAtPath]
  private val monitorActor = actorSystem.actorOf(MonitorActor(concurrency = 1))

  /**
   * Given an path event kind, returns a function literal that is applied with a path
   * and pushes a EventAtPath into the rxSubject using the path amd a closure of
   * the event kind that it was created with
   */
  private def pushNextPathToSubject(eventKind: WatchEvent.Kind[Path]): Function[Path, Unit] =
    { p: Path => rxSubject.onNext(EventAtPath(p, eventKind)) }

  /**
   * Returns an Observable that will spew out [[Path]]s over time based on
   * what paths are registered and unregistered to this RxMonitor
   */
  def observable(): Observable[EventAtPath] = toScalaObservable(rxSubject.asObservable())

  /**
   * Registers a path for monitoring
   *
   * Note that this is an asynchronous operation
   */
  def registerPath(event: WatchEvent.Kind[Path],
                   path: Path,
                   recursive: Boolean = false,
                   modifier: Option[Modifier] = None) {
    monitorActor ! RegisterBossyCallback(
      event = event,
      modifier = modifier,
      recursive = recursive,
      path = path,
      callback = pushNextPathToSubject(event))
  }

  /**
   * Unregisters a path from monitoring
   *
   * Note that this is an asynchronous operation
   */
  def unregisterPath(event: WatchEvent.Kind[Path],
                     path: Path,
                     recursive: Boolean = false,
                     modifier: Option[Modifier] = None) {
    monitorActor ! UnRegisterCallback(
      event = event,
      recursive = recursive,
      path = path)
  }

  /**
   * Stops any kind of monitoring and signals to the observers of this
   * RxMonitor instance that the Observable is completed.
   */
  def stop() {
    monitorActor ! PoisonPill
    rxSubject.onCompleted()
  }
}
