/*
Copyright (c) 2011-2013 Robby, Kansas State University.        
All rights reserved. This program and the accompanying materials      
are made available under the terms of the Eclipse Public License v1.0 
which accompanies this distribution, and is available at              
http://www.eclipse.org/legal/epl-v10.html                             
*/

package org.sireum.kiasan

import scala.concurrent.forkjoin._
import scala.collection.parallel._
import com.typesafe.scalalogging._
import org.sireum.kiasan.state._
import org.sireum.pilar.state._
import org.sireum.pilar.ast._
import org.sireum.pilar.eval._
import org.sireum.topi._
import org.sireum.util._

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
object Kiasan {
  type KiasanState[S <: KiasanState[S]] = State[S] with KiasanStatePart[S] with ScheduleRecordingState[S]

  case class TopiCache(state : org.sireum.topi.TopiState,
                       lastCompiledLength : Int) extends Immutable

  def replay[S <: KiasanState[S], R, C](
    locationProvider : KiasanLocationProvider[S],
    evaluator : Evaluator[S, R, C, ISeq[S]],
    initialStatesProvider : KiasanInitialStateProvider[S],
    depthBound : Int) : S = {

    var Seq(s) = initialStatesProvider.initialStates
    var i = 0
    while (!isTerminal(s) && i < depthBound) {
      val nextStates =
        for {
          (s2, l, t) <- schedule(s, evaluator.transitions(s, locationProvider.location(s)).enabled)
          nextS <- evaluator.evalTransformation(s2, l, t)
        } yield nextS
      s = nextStates(0)
      i += 1
    }
    s
  }

  def isTerminal[S <: KiasanState[S]](s : S) : Boolean =
    s.callStack.isEmpty || s.assertionViolation.isDefined ||
      s.assumptionBreach.isDefined

  def schedule[S <: KiasanState[S]](
    s : S, slts : ISeq[(S, LocationDecl, Transformation)]) : ISeq[(S, LocationDecl, Transformation)] = {
    val n = slts.length
    if (n <= 1) slts
    else {
      s match {
        case s : ScheduleRecordingState[S] =>
          s.peekSchedule match {
            case None =>
              slts.zip(0 until n).map { sltj =>
                val ((s2, l, t), j) = sltj
                (s2.asInstanceOf[ScheduleRecordingState[_]].
                  recordSchedule("trans", n, j).asInstanceOf[S], l, t)
              }
            case Some((sourceOpt, num, i)) =>
              assert(sourceOpt == Some("trans") && num == slts.length)
              val (s2, l, t) = slts(i)
              ivector((s2.asInstanceOf[ScheduleRecordingState[_]].
                popSchedule.asInstanceOf[S], l, t))
          }
        case _ => slts
      }
    }
  }
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait KiasanInitialStateProvider[S <: Kiasan.KiasanState[S]] {
  def initialStates : ISeq[S]
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait KiasanLocationProvider[S <: Kiasan.KiasanState[S]] {
  def location(s : S) : LocationDecl
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait KiasanReporter[S <: Kiasan.KiasanState[S]] {
  def foundAssertionViolation(s : S, depth : Int) : S
  def foundAssumptionBreach(s : S, depth : Int) : S
  def foundEndState(s : S, depth : Int) : S
  def foundDepthBoundExhaustion(s : S, depth : Int) : S
  def console(s : String) {
    println(s)
  }
  def statistics(numOfFeasibleStates : Int, numOfInfeasibleStates : Int,
                 dpTimeInMs : Long) {}
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait Kiasan {
  def search : this.type
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait KiasanBfs[S <: Kiasan.KiasanState[S], R, C] extends Kiasan with Logging {
  import Kiasan._
  import State._

  var dpTime = 0l

  var dpTotalTime = 0l

  val topiQueue = new java.util.concurrent.ConcurrentLinkedQueue[Topi]

  def parallelMode : Boolean

  def parallelThreshold : Int

  def parallelismLevel : Int

  def locationProvider : KiasanLocationProvider[S]

  def evaluator : Evaluator[S, R, C, ISeq[S]]

  def initialStatesProvider : KiasanInitialStateProvider[S]

  def reporter : KiasanReporter[S]

  def createTopi : Topi

  def depthBound : Int

  lazy val taskSupport = new ForkJoinTaskSupport(new ForkJoinPool(parallelismLevel))

  def topiPool[T](f : Topi => T) : T = {
    var t = topiQueue.poll
    if (t == null) {
      t = createTopi
    }
    try f(t) finally topiQueue.offer(t)
  }

  def search : this.type = {
    var workList : GenSeq[S] = initialStatesProvider.initialStates

    var i = 0
    val depth = depthBound

    var searchTime = System.currentTimeMillis
    while (!workList.isEmpty && i < depth) {
      val ps = inconNextStatesPairs(workList)
      val inconsistencyCheckRequested = ps.exists(first2)
      val nextStates = ps.flatMap(second2)

      i += 1

      workList =
        filterTerminatingStates(inconsistencyCheckRequested, nextStates, i)
    }

    if (i >= depth) {
      workList.foreach(s => reporter.foundDepthBoundExhaustion(s, i))
    }

    {
      import scala.collection.JavaConversions._
      for (t <- topiQueue.par) t.close
    }

    searchTime = System.currentTimeMillis - searchTime

    logger.debug((({ pLevel : String =>
      var parInfo =
        if (parallelMode)
          s"""
Parallelism level: ${pLevel}
Parallel threshold: ${parallelThreshold}"""
        else ""
      s"""
Parallel mode: ${parallelMode}${parInfo}
Depth bound: ${depthBound}
Search time: ${searchTime} ms
DP time: ${dpTime} (${Math.round(dpTime * 100d / searchTime)}%) ms"""
    })(
      if (parallelismLevel >= 2) parallelismLevel.toString
      else "default")))

    this
  }

  @inline
  protected def check(s : S) : (S, TopiResult.Type) =
    topiPool { topi =>
      val topiCachePropKey = "Topi Cache"
      val tc = s.getPropertyOrElse[TopiCache](topiCachePropKey, TopiCache(topi.newState, 0))
      val newTc = TopiCache(topi.compile(s.pathConditions, tc.state), s.pathConditions.length)
      val s2 = s.setProperty(topiCachePropKey, newTc)

      val r = topi.check(newTc.state)
      (s2, r)
    }

  @inline
  private def par[T](shouldParallize : Boolean, l : GenSeq[T]) =
    if (parallelMode && shouldParallize && parallelThreshold < l.size) {
      val pl = l.par
      if (parallelismLevel >= 2)
        pl.tasksupport = taskSupport
      (pl, true)
    } else (l.seq, false)

  @inline
  private def inconNextStatesPairs(l : GenSeq[S]) =
    first2(par(true, l)).map { s =>
      var inconsistencyCheckRequested = false
      val nextStates =
        for {
          (s2, l, t) <- schedule(s, evaluator.transitions(s, locationProvider.location(s)).enabled)
          nextS <- evaluator.evalTransformation(s2, l, t)
        } yield {
          inconsistencyCheckRequested =
            inconsistencyCheckRequested || nextS.inconsistencyCheckRequested
          nextS
        }

      (inconsistencyCheckRequested, nextStates)
    }

  @inline
  private def filterTerminatingStates(inconsistencyCheckRequested : Boolean,
                                      states : GenSeq[S], depth : Int) = {
    val (gs, isPar) = par(inconsistencyCheckRequested, states)
    val stateTimePairs =
      gs.flatMap { s =>
        var time = 0l
        if (s.callStack.isEmpty) {
          reporter.foundEndState(s, depth)
          None
        } else {
          val s3Opt =
            if (s.inconsistencyCheckRequested) {
              time = System.currentTimeMillis
              val (s2, tr) = check(s)
              time = System.currentTimeMillis - time
              if (tr == org.sireum.topi.TopiResult.UNSAT) None
              else Some(s2.requestInconsistencyCheck(false))
            } else Some(s.requestInconsistencyCheck(false))

          if (s3Opt.isDefined) {
            val s3 = s3Opt.get
            if (s3.assertionViolation.isDefined) {
              reporter.foundAssertionViolation(s3, depth)
              None
            } else if (s3.assumptionBreach.isDefined) {
              reporter.foundAssumptionBreach(s3, depth)
              None
            } else {
              Some(s3, time)
            }
          } else None
        }
      }
    if (!stateTimePairs.isEmpty) {
      dpTime += stateTimePairs.map(second2).reduce(
        if (isPar) dpTimeMaxF else dpTimeTotalF
      )
      dpTotalTime += stateTimePairs.map(second2).reduce(dpTimeTotalF)
    }

    stateTimePairs.map(first2)
  }

  def dpTimeMaxF(t1 : Long, t2 : Long) = if (t1 < t2) t2 else t1
  def dpTimeTotalF(t1 : Long, t2 : Long) = t1 + t2
}
