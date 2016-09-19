/*
 * Copyright 2014–2016 SlamData Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package quasar.physical.sparkcore.fs

import quasar.Predef._
import quasar.qscript.MapFuncs._
import quasar.std.DateLib
import quasar.Data
import quasar.qscript._
import quasar.Planner._
import quasar.SKI.κ

import java.math.{BigDecimal => JBigDecimal}

import org.threeten.bp.{Instant, ZoneOffset}
import matryoshka.{Hole => _, _}, Recursive.ops._
import scalaz.{Divide => _, _}, Scalaz._

object CoreMap {

  // TODO: replace Data.NA with something safer
  def change[T[_[_]] : Recursive]: AlgebraM[PlannerError \/ ?, MapFunc[T, ?], Data => Data] = {
    case Constant(f) => κ[Data, Data](f.cata(Data.fromEJson)).right
    case Undefined() => κ[Data, Data](Data.NA).right // TODO compback to this one, needs reviewv

    case Length(f) => (f >>> {
      case Data.Str(v) => Data.Int(v.length)
      case Data.Arr(v) => Data.Int(v.size)
      case _ => Data.NA 
    }).right

    case Date(f) => (f >>> {
      case Data.Str(v) => DateLib.parseDate(v).getOrElse(Data.NA)
      case _ => Data.NA
    }).right
    case Time(f) => (f >>> {
      case Data.Str(v) => DateLib.parseTime(v).getOrElse(Data.NA)
      case _ => Data.NA
    }).right
    case Timestamp(f) => (f >>> {
      case Data.Str(v) => DateLib.parseTimestamp(v).getOrElse(Data.NA)
      case _ => Data.NA
    }).right
    case Interval(f) => (f >>> {
      case Data.Str(v) => DateLib.parseInterval(v).getOrElse(Data.NA)
      case _ => Data.NA
    }).right
    case TimeOfDay(f) => (f >>> {
      case Data.Timestamp(v) => Data.Time(v.atZone(ZoneOffset.UTC).toLocalTime)
      case _ => Data.NA
    }).right
    case ToTimestamp(f) => (f >>> {
      case Data.Int(epoch) => Data.Timestamp(Instant.ofEpochMilli(epoch.toLong))
      case _ => Data.NA
    }).right
    case Extract(f1, f2) => ??? // DONTKNOW
    case Now() => ((x: Data) => Data.Timestamp(Instant.now())).right

    case Negate(f) => (f >>> {
      case Data.Int(v) => Data.Int(-v)
      case Data.Dec(v) => Data.Dec(-v)
      case Data.Interval(v) => Data.Interval(v.negated())
      case _ => Data.NA
    }).right
    // TODO not all permutations covered (should they be covered?)
    case Add(f1, f2) => ((x: Data) => add(f1(x), f2(x))).right
    case Multiply(f1, f2) => ((x: Data) => multiply(f1(x), f2(x))).right
    case Subtract(f1, f2) => ((x: Data) => subtract(f1(x), f2(x))).right
    case Divide(f1, f2) => ((x: Data) => divide(f1(x), f2(x))).right
    case Modulo(f1, f2) => ((x: Data) => modulo(f1(x), f2(x))).right
    case Power(f1, f2) => ((x: Data) => power(f1(x), f2(x))).right

    case Not(f) => (f >>> {
      case Data.Bool(b) => Data.Bool(!b)
      case _ => Data.NA
    }).right
    case Eq(f1, f2) => ((x: Data) => eq(f1(x), f2(x))).right
    case Neq(f1, f2) => ((x: Data) => eq(f1(x), f2(x)) match {
      case Data.Bool(b) => Data.Bool(!b)
      case _ => Data.NA
    }).right
    case Lt(f1, f2) => ((x: Data) => lt(f1(x), f2(x))).right
    case Lte(f1, f2) => ((x: Data) => lte(f1(x), f2(x))).right
    case Gt(f1, f2) => ((x: Data) => gt(f1(x), f2(x))).right
    case Gte(f1, f2) => ((x: Data) => gte(f1(x), f2(x))).right
    case IfUndefined(f1, f2) => ??? // TODO
    case And(f1, f2) => ((x: Data) => (f1(x), f2(x)) match {
      case (Data.Bool(a), Data.Bool(b)) => Data.Bool(a && b)
      case _ => Data.NA
    }).right
    case Or(f1, f2) => ((x: Data) => (f1(x), f2(x)) match {
      case (Data.Bool(a), Data.Bool(b)) => Data.Bool(a || b)
      case _ => Data.NA
    }).right
    case Coalesce(f1, f2) => ??? // TODO
    case Between(f1, f2, f3) => ((x: Data) => between(f1(x), f2(x), f3(x))).right
    case ToString(f) => (f >>> toStringFunc).right
    case _ => InternalError("not implemented").left
  }

  private def add(d1: Data, d2: Data): Data = (d1, d2) match {
    case (Data.Int(a), Data.Int(b)) => Data.Int(a + b)
    case (Data.Int(a), Data.Dec(b)) => Data.Dec(BigDecimal(new JBigDecimal(a.bigInteger)) + b)
    case (Data.Int(a), Data.Interval(b)) => ???
    case (Data.Dec(a), Data.Int(b)) => Data.Dec(a + BigDecimal(new JBigDecimal(b.bigInteger)))
    case (Data.Dec(a), Data.Dec(b)) => Data.Dec(a + b)
    case (Data.Dec(a), Data.Interval(b)) => ???
    case (Data.Interval(a), Data.Int(b)) => ???
    case (Data.Interval(a), Data.Dec(b)) => ???
    case (Data.Interval(a), Data.Interval(b)) => Data.Interval(a.plus(b))
    case (Data.Timestamp(a), Data.Int(b)) => ???
    case (Data.Timestamp(a), Data.Dec(b)) => ???
    case (Data.Timestamp(a), Data.Interval(b)) => Data.Timestamp(a.plus(b))
    case (Data.Date(a), Data.Int(b)) => ???
    case (Data.Date(a), Data.Dec(b)) => ???
    case (Data.Date(a), Data.Interval(b)) => Data.Date(a.plus(b))
    case (Data.Time(a), Data.Int(b)) => ???
    case (Data.Time(a), Data.Dec(b)) => ???
    case (Data.Time(a), Data.Interval(b)) => Data.Time(a.plus(b))
    case _ => Data.NA
  }

  private def subtract(d1: Data, d2: Data): Data = (d1, d2) match {
    case (Data.Dec(a), Data.Dec(b)) => Data.Dec(a - b)
    case (Data.Dec(a), Data.Int(b)) => Data.Dec(a - BigDecimal(new JBigDecimal(b.bigInteger)))
    case (Data.Int(a), Data.Int(b)) => Data.Int(a - b)
    case (Data.Int(a), Data.Dec(b)) => Data.Dec(BigDecimal(new JBigDecimal(a.bigInteger)) - b)
    case (Data.Interval(a), Data.Interval(b)) => Data.Interval(a.minus(b))
    case (Data.Date(a), Data.Interval(b)) => Data.Date(a.minus(b))
    case (Data.Time(a), Data.Interval(b)) => Data.Time(a.minus(b))
    case (Data.Timestamp(a), Data.Interval(b)) => Data.Timestamp(a.minus(b))
    case _ => Data.NA
  }

  private def multiply(d1: Data, d2: Data): Data = (d1, d2)  match {
    case (Data.Dec(a), Data.Dec(b)) => Data.Dec(a * b)
    case (Data.Dec(a), Data.Int(b)) => Data.Dec(a * BigDecimal(new JBigDecimal(b.bigInteger)))
    case (Data.Int(a), Data.Int(b)) => Data.Int(a * b)
    case (Data.Int(a), Data.Dec(b)) => Data.Dec(BigDecimal(new JBigDecimal(a.bigInteger)) * b)
    case (Data.Interval(a), Data.Dec(b)) => Data.Interval(a.multipliedBy(b.toLong))
    case (Data.Interval(a), Data.Int(b)) => Data.Interval(a.multipliedBy(b.toLong))
    case _ => Data.NA
  }

  private def divide(d1: Data, d2: Data): Data = (d1, d2) match {
    // TODO missibin Temporal
    case (Data.Dec(a), Data.Dec(b)) => Data.Dec(a / b)
    case (Data.Dec(a), Data.Int(b)) => Data.Dec(a / BigDecimal(new JBigDecimal(b.bigInteger)))
    case (Data.Int(a), Data.Int(b)) => Data.Int(a / b)
    case (Data.Int(a), Data.Dec(b)) => Data.Dec(BigDecimal(new JBigDecimal(a.bigInteger)) / b)
    case (Data.Interval(a), Data.Dec(b)) => Data.Interval(a.multipliedBy(b.toLong))
    case (Data.Interval(a), Data.Int(b)) => Data.Interval(a.multipliedBy(b.toLong))
    case _ => Data.NA
  }

  //MathRel Numeric
  private def modulo(d1: Data, d2: Data) = (d1, d2) match {
    case (Data.Int(a), Data.Int(b)) => Data.Int(a % b)
    case (Data.Int(a), Data.Dec(b)) => ???
    case (Data.Dec(a), Data.Int(b)) => ???
    case (Data.Dec(a), Data.Dec(b)) => ???
    case (Data.Interval(a), Data.Int(b)) => ???
    case (Data.Interval(a), Data.Dec(b)) => ???
    case _ => Data.NA
  }

  private def power(d1: Data, d2: Data): Data = (d1, d2) match {
    case (Data.Int(a), Data.Int(b)) => Data.Int(a ^ b)
    case (Data.Int(a), Data.Dec(b)) => ???
    case (Data.Dec(a), Data.Int(b)) => ???
    case (Data.Dec(a), Data.Dec(b)) => ???
    case _ => Data.NA
  }

  private def eq(d1: Data, d2: Data): Data = (d1, d2) match {
    case (Data.Null, Data.Null) => Data.Bool(true) // is it really?
    case (Data.Str(a), Data.Str(b)) => Data.Bool(a == b)
    case (Data.Bool(a), Data.Bool(b)) => Data.Bool(a == b)
    case (Data.Int(a), Data.Int(b)) => Data.Bool(a == b)
    case (Data.Dec(a), Data.Dec(b)) => Data.Bool(a == b)
    case (Data.Obj(a), Data.Obj(b)) => ??? // TODO
    case (Data.Arr(a), Data.Arr(b)) => if(a.size != b.size) Data.Bool(false) else {
      a.zip(b)
        .map {
        case (d1, d2) => eq(d1, d2)
      }.fold(Data.Bool(true)){
        case (Data.Bool(b1), Data.Bool(b2)) => Data.Bool(b1 && b2)
        case _ => Data.NA
      }
    }
    case (Data.Set(a), Data.Set(b)) => if(a.size != b.size) Data.Bool(false) else {
      a.zip(b)
        .map {
        case (d1, d2) => eq(d1, d2)
      }.fold(Data.Bool(true)){
        case (Data.Bool(b1), Data.Bool(b2)) => Data.Bool(b1 && b2)
        case _ => Data.NA
      }
    }
    case (Data.Timestamp(a), Data.Timestamp(b)) => Data.Bool(a == b)
    case (Data.Date(a), Data.Date(b)) => Data.Bool(a == b)
    case (Data.Time(a), Data.Time(b)) => Data.Bool(a == b)
    case (Data.Interval(a), Data.Interval(b)) => Data.Bool(a == b)
    case (Data.Binary(a), Data.Binary(b)) => Data.Bool(a == b)
    case (Data.Id(a), Data.Id(b)) => Data.Bool(a == b)
    case (Data.NA, Data.NA) => Data.Bool(true) // is it really?
    case _ => Data.Bool(false)
  }

  //  Numeric ⨿ Interval ⨿ Str ⨿ Temporal ⨿ Bool
  private def lt(d1: Data, d2: Data): Data = (d1, d2) match {
    case (Data.Int(a), Data.Int(b)) => Data.Bool(a < b)
    case (Data.Int(a), Data.Dec(b)) => ??? // TODO
    case (Data.Int(a), Data.Interval(b)) => ???
    case (Data.Int(a), Data.Str(b)) => ???
    case (Data.Int(a), Data.Timestamp(b)) => ???
    case (Data.Int(a), Data.Date(b)) => ???
    case (Data.Int(a), Data.Time(b)) => ???
    case (Data.Int(a), Data.Bool(b)) => ???
    case (Data.Dec(a), Data.Dec(b)) => Data.Bool(a < b)
    case (Data.Interval(a), Data.Interval(b)) => Data.Bool(a.compareTo(b) < 0)
    case (Data.Str(a), Data.Str(b)) => Data.Bool(a.compareTo(b) < 0)
    case (Data.Timestamp(a), Data.Timestamp(b)) => Data.Bool(a.compareTo(b) < 0)
    case (Data.Date(a), Data.Date(b)) => Data.Bool(a.compareTo(b) < 0)
    case (Data.Time(a), Data.Time(b)) => Data.Bool(a.compareTo(b) < 0)
    case (Data.Bool(a), Data.Bool(b)) => (a, b) match {
      case (false, true) => Data.Bool(true)
      case _ => Data.Bool(false)
    }
    case _ => Data.NA
  }

  private def lte(d1: Data, d2: Data): Data = (d1, d2) match {
    case (Data.Int(a), Data.Int(b)) => Data.Bool(a <= b)
    // TODO same as lt
    case (Data.Dec(a), Data.Dec(b)) => Data.Bool(a <= b)
    case (Data.Interval(a), Data.Interval(b)) => Data.Bool(a.compareTo(b) <= 0)
    case (Data.Str(a), Data.Str(b)) => Data.Bool(a.compareTo(b) <= 0)
    case (Data.Timestamp(a), Data.Timestamp(b)) => Data.Bool(a.compareTo(b) <= 0)
    case (Data.Date(a), Data.Date(b)) => Data.Bool(a.compareTo(b) <= 0)
    case (Data.Time(a), Data.Time(b)) => Data.Bool(a.compareTo(b) <= 0)
    case (Data.Bool(a), Data.Bool(b)) => (a, b) match {
      case (true, false) => Data.Bool(false)
      case _ => Data.Bool(true)
    }
    case _ => Data.NA
  }

  private def gt(d1: Data, d2: Data): Data = (d1, d2) match {
    case (Data.Int(a), Data.Int(b)) => Data.Bool(a > b)
    // TODO same as lt
    case (Data.Dec(a), Data.Dec(b)) => Data.Bool(a > b)
    case (Data.Interval(a), Data.Interval(b)) => Data.Bool(a.compareTo(b) > 0)
    case (Data.Str(a), Data.Str(b)) => Data.Bool(a.compareTo(b) > 0)
    case (Data.Timestamp(a), Data.Timestamp(b)) => Data.Bool(a.compareTo(b) > 0)
    case (Data.Date(a), Data.Date(b)) => Data.Bool(a.compareTo(b) > 0)
    case (Data.Time(a), Data.Time(b)) => Data.Bool(a.compareTo(b) > 0)
    case (Data.Bool(a), Data.Bool(b)) => (a, b) match {
      case (true, false) => Data.Bool(true)
      case _ => Data.Bool(false)
    }
    case _ => Data.NA
  }

  private def gte(d1: Data, d2: Data): Data = (d1, d2) match {
    case (Data.Int(a), Data.Int(b)) => Data.Bool(a >= b)
    // TODO same as lt
    case (Data.Dec(a), Data.Dec(b)) => Data.Bool(a >= b)
    case (Data.Interval(a), Data.Interval(b)) => Data.Bool(a.compareTo(b) >= 0)
    case (Data.Str(a), Data.Str(b)) => Data.Bool(a.compareTo(b) >= 0)
    case (Data.Timestamp(a), Data.Timestamp(b)) => Data.Bool(a.compareTo(b) >= 0)
    case (Data.Date(a), Data.Date(b)) => Data.Bool(a.compareTo(b) >= 0)
    case (Data.Time(a), Data.Time(b)) => Data.Bool(a.compareTo(b) >= 0)
    case (Data.Bool(a), Data.Bool(b)) => (a, b) match {
      case (false, true) => Data.Bool(false)
      case _ => Data.Bool(true)
    }
    case _ => Data.NA
  }

  private def between(d1: Data, d2: Data, d3: Data): Data = ???
  
  private def toStringFunc: Data => Data = {
    case Data.Null => Data.Str("null")
    case d: Data.Str => d
    case Data.Bool(v) => Data.Str(v.toString)
    case Data.Dec(v) => Data.Str(v.toString)
    case Data.Int(v) => Data.Str(v.toString)
    // TODO how to handle obj and collections
    case Data.Obj(v) => ???
    case Data.Arr(v) => ???
    case Data.Set(v) => ???
    case Data.Timestamp(v) => Data.Str(v.toString)
    case Data.Date(v) => Data.Str(v.toString)
    case Data.Time(v) => Data.Str(v.toString)
    case Data.Interval(v) => Data.Str(v.toString)
    case Data.Binary(v) => Data.Str(v.toList.mkString(""))
    case Data.Id(s) => Data.Str(s)
    // TODO fails on d: Data.NA
    case d => d
  }

}
