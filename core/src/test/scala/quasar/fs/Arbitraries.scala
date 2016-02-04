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

package quasar.fs

import quasar.Predef.Option

import org.scalacheck.Gen

trait Arbitraries extends
  NumericArbitrary with
  FileSystemTypeArbitrary with
  NonEmptyStringArbitrary with
  PathArbitrary with
  InMemoryArbitrary with
  MoveSemanticsArbitrary

object Arbitraries extends Arbitraries {
  // TODO: Replace with built-in version when we update scalacheck
  def genOption[A](gen: Gen[Option[A]]): Gen[A] =
    gen flatMap (_.fold(Gen.fail[A])(Gen.const))
}
