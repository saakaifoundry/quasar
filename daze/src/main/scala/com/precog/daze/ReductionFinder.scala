/*
 *  ____    ____    _____    ____    ___     ____ 
 * |  _ \  |  _ \  | ____|  / ___|  / _/    / ___|        Precog (R)
 * | |_) | | |_) | |  _|   | |     | |  /| | |  _         Advanced Analytics Engine for NoSQL Data
 * |  __/  |  _ <  | |___  | |___  |/ _| | | |_| |        Copyright (C) 2010 - 2013 SlamData, Inc.
 * |_|     |_| \_\ |_____|  \____|   /__/   \____|        All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the 
 * GNU Affero General Public License as published by the Free Software Foundation, either version 
 * 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See 
 * the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this 
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.precog
package daze

import scalaz.Monoid

import scala.collection.mutable

import com.precog.util._

import scalaz.NonEmptyList
import scalaz.std.map._

trait ReductionFinder extends DAG {
  import instructions._
  import dag._

  def findReductions(node: DepGraph): Map[DepGraph, NonEmptyList[Reduction]] = node.foldDown[Map[DepGraph, NonEmptyList[Reduction]]] {
    case dag.Reduce(_, red, parent) => Map(parent -> NonEmptyList(red))
  }

  def megaReduce(node: DepGraph, reds: Map[DepGraph, NonEmptyList[Reduction]]): DepGraph = {
    val reduceTable = mutable.Map[DepGraph, dag.MegaReduce]()  //this is a Map from parent node to MegaReduce node

    node.mapDown { recurse => {
      case graph @ dag.Reduce(loc, red, parent) if reds isDefinedAt parent => {
        val left = reduceTable.get(parent) getOrElse {
          val result = dag.MegaReduce(loc, reds(parent), recurse(parent))
          reduceTable += (parent -> result)
          result
        }
        val index: Int = reds(parent).list.indexOf(red)
        dag.Join(loc, DerefArray, CrossLeftSort, left, Root(loc, PushNum(index.toString)))
      }
    }}
  }
}
