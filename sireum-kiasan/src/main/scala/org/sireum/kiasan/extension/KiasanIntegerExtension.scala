/*
Copyright (c) 2011-2012 Robby, Kansas State University.        
All rights reserved. This program and the accompanying materials      
are made available under the terms of the Eclipse Public License v1.0 
which accompanies this distribution, and is available at              
http://www.eclipse.org/legal/epl-v10.html                             
*/

package org.sireum.kiasan.extension

import org.sireum.extension._
import org.sireum.extension.IntegerExtension._
import org.sireum.extension.annotation._
import org.sireum.kiasan.state._
import org.sireum.kiasan.extension.annotation._
import org.sireum.konkrit.extension._
import org.sireum.pilar.ast._
import org.sireum.pilar.eval._
import org.sireum.pilar.state._
import org.sireum.topi.process._
import org.sireum.topi.annotation._
import org.sireum.util._
import org.sireum.util.math._

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
object KiasanIntegerExtension extends ExtensionCompanion {
  def create[S <: KiasanStatePart[S]](
    config : EvaluatorConfiguration[S, Value, ISeq[(S, Value)], ISeq[(S, Boolean)], ISeq[S]]) =
    new KiasanIntegerBExtension(config)

  val Type = "pilar://typeext/" + UriUtil.classUri(this) + "/Type"

  @BackEnd(value = "Z3", mode = "Process")
  def z3BackEndPart = new TopiProcess.BackEndPart {
    def expTranslator(sb : StringBuilder, mm : MMap[Immutable, Int]) = {
      val lineSep = System.getProperty("line.separator")
      var lastNum = mm.getOrElse(Type, -1)

        @inline
        implicit def i2s(i : Int) = i.toString

        @inline
        def println(ss : String*) {
          for (s <- ss)
            sb.append(s)
          sb.append(lineSep)
        }

        @inline
        def declareConst(num : Int) {
          if (num > lastNum) {
            for (i <- lastNum + 1 to num)
              println("(declare-const ii!", i, " Int)")
            lastNum = num
            mm(Type) = lastNum
          }
        }

        @inline
        def un(freshNum : Int, n : String) {
          declareConst(freshNum)
          println("(assert (= ii!", freshNum, " (- ", n, ")))")
        }

        @inline
        def bin(freshNum : Int, n : String, m : String, op : String) {
          declareConst(freshNum)
          println("(assert (= ii!", freshNum, " (", op, " ", n, " ", m, ")))")
        }

        @inline
        def sbin(v1 : String, v2 : String, op : String) {
          println("(assert (", op, " ", v1, " ", v2, "))")
        }

        @inline
        def nsbin(v1 : String, v2 : String, op : String) {
          println("(assert (not (", op, " ", v1, " ", v2, ")))")
        }

        @inline
        implicit def v2s : Exp --> String = {
          case e : LiteralExp =>
            e.literal.toString
          case ValueExp(c : KonkritIntegerValue) =>
            val n = c.value
            if (n < 0)
              "(- " + (-n).toBigInt + ")"
            else
              n.toString
          case ValueExp(KI(num)) =>
            declareConst(num)
            "ii!" + num
        }

      {
        case BinaryExp("==", ValueExp(KI(freshNum)), BinaryExp(op, n, m)) // 
        if (v2s isDefinedAt n) && (v2s isDefinedAt m) && (op == "+" || op == "-" || op == "*" || op == "/" || op == "%") =>
          op match {
            case "+" => bin(freshNum, n, m, "+")
            case "-" => bin(freshNum, n, m, "-")
            case "*" => bin(freshNum, n, m, "*")
            case "/" => bin(freshNum, n, m, "div")
            case "%" => bin(freshNum, n, m, "rem")
          }
        case BinaryExp(op, n, m) if (v2s isDefinedAt n) && (v2s isDefinedAt m) &&
          (op == "==" || op == "!=" || op == "<" || op == "<=" || op == ">" || op == ">=") =>
          op match {
            case "==" => sbin(n, m, "=")
            case "!=" => nsbin(n, m, "=")
            case ">"  => sbin(n, m, ">")
            case ">=" => sbin(n, m, ">=")
            case "<"  => sbin(n, m, "<")
            case "<=" => sbin(n, m, "<=")
          }
        case BinaryExp("==", ValueExp(KI(freshNum)), UnaryExp("-", v)) =>
          un(freshNum, v)
      }
    }

    def stateRewriter(m : IMap[String, Value]) = {
      case v @ KI(num) => m.getOrElse("ii!" + num, v)
    }
  }
}

import org.sireum.kiasan.extension.KiasanExtension._

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait KiasanIntegerValue extends IntegerValue with KiasanValue

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
final case class KI(num : Int) extends KiasanIntegerValue {
  val typeUri = KiasanIntegerExtension.Type
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait KiasanIntegerExtension[S <: KiasanStatePart[S]]
    extends Extension[S, Value, ISeq[(S, Value)], ISeq[(S, Boolean)], ISeq[S]] {

  import KiasanIntegerExtension._

  val uriPath = UriUtil.classUri(this)

  type C = KonkritIntegerValue
  type K = KiasanIntegerValue

  @inline
  private implicit def re2r(p : (S, Value)) = ilist(p)

  @inline
  final implicit def v2e(v : Value) : Exp = ValueExp(v)

  @Cast
  def castType : (S, Value, ResourceUri) --> ISeq[(S, Value)] = {
    case (s, v : K, IntegerExtension.Type)       => (s, v)
    case (s, v : K, KiasanIntegerExtension.Type) => (s, v)
  }

  @FreshKiasanValue
  def freshKI : (S, ResourceUri) --> (S, Value) = {
    case (s, KiasanIntegerExtension.Type) => {
      val (nextS, num) = s.next(KiasanIntegerExtension.Type)
      (nextS, KI(num))
    }
  }

  @Binaries(Array("+", "-", "*", "/", "%"))
  def binopAEval : (S, Value, String, Value) --> ISeq[(S, Value)] = {
    case (s, v : C, opA : String, w : K) => binopAHelper(s, v, opA, w)
    case (s, v : K, opA : String, w : C) => binopAHelper(s, v, opA, w)
    case (s, v : K, opA : String, w : K) => binopAHelper(s, v, opA, w)
  }

  @inline
  private def binopAHelper(s : S, v : Value, opA : String, w : Value) : ISeq[(S, Value)] = {
    val (nextS, a) = freshKI(s, KiasanIntegerExtension.Type)
    (nextS.addPathCondition(BinaryExp("==", a, BinaryExp(opA, v, w))), a)
  }

  @Binaries(Array("==", "!=", ">", ">=", "<", "<="))
  def binopREval : (S, Value, String, Value) --> ISeq[(S, Value)] = {
    case (s, v : C, opR : String, w : K) => binopRHelper(s, v, opR, w)
    case (s, v : K, opR : String, w : C) => binopRHelper(s, v, opR, w)
    case (s, v : K, opR : String, w : K) => binopRHelper(s, v, opR, w)
  }

  @inline
  private def binopRHelper(s : S, v : Value, opR : String, w : Value) : ISeq[(S, Value)] = {
    ilist(
      (s.addPathCondition(BinaryExp(opR, v, w)).requestInconsistencyCheck, b2v(true)),
      (s.addPathCondition(BinaryExp(comp(opR), v, w)).requestInconsistencyCheck, b2v(false)))
  }

  def b2v(b : Boolean) : Value

  @Unaries(Array("-", "+"))
  def unopAEval : (S, String, Value) --> ISeq[(S, Value)] = {
    case (s, opA, v : K) => unopAHelper(s, opA, v)
  }

  @inline
  private def unopAHelper(s : S, opA : String, v : Value) : ISeq[(S, Value)] = {
    opA match {
      case "-" =>
        val (nextS, a) = freshKI(s, KiasanIntegerExtension.Type)
        (nextS.addPathCondition(BinaryExp("==", a, UnaryExp(opA, v))), a)
      case "+" => (s, v)
    }
  }

  val comp =
    Map("==" -> "!=",
      "!=" -> "==",
      ">" -> "<=",
      ">=" -> "<",
      "<" -> ">=",
      "<=" -> ">=")
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
object KiasanIntegerBExtension extends ExtensionCompanion {
  def create[S <: KiasanStatePart[S]](
    config : EvaluatorConfiguration[S, Value, ISeq[(S, Value)], ISeq[(S, Boolean)], ISeq[S]]) =
    new KiasanIntegerBExtension(config)
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
final class KiasanIntegerBExtension[S <: KiasanStatePart[S]](
  config : EvaluatorConfiguration[S, Value, ISeq[(S, Value)], ISeq[(S, Boolean)], ISeq[S]])
    extends KiasanIntegerExtension[S] {
  import KonkritBooleanExtension._

  def b2v(b : Boolean) : Value = if (b) TT else FF
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
object KiasanIntegerIExtension extends ExtensionCompanion {
  def create[S <: KiasanStatePart[S]](
    config : EvaluatorConfiguration[S, Value, ISeq[(S, Value)], ISeq[(S, Boolean)], ISeq[S]]) =
    new KiasanIntegerIExtension(config)
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
final class KiasanIntegerIExtension[S <: KiasanStatePart[S]](
  config : EvaluatorConfiguration[S, Value, ISeq[(S, Value)], ISeq[(S, Boolean)], ISeq[S]])
    extends KiasanIntegerExtension[S] {
  import KiasanIntegerExtension._

  def b2v(b : Boolean) : Value = if (b) CI(SireumNumber(1)) else CI(SireumNumber(0))

  @Cond
  def cond : (S, Value) --> ISeq[(S, Boolean)] = {
    case (s, v : K) =>
      val w = CI(SireumNumber(0))
      ilist(
        (s.addPathCondition(BinaryExp("==", v, w)).requestInconsistencyCheck, true),
        (s.addPathCondition(BinaryExp("!=", v, w)).requestInconsistencyCheck, false))
  }
}