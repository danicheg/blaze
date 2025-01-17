/*
 * Copyright 2014 http4s.org
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

package org.http4s.blaze.http.http2

import java.nio.ByteBuffer

import org.specs2.mutable.Specification

class ByteBufferInputStreamSpec extends Specification {
  private def fromByteBuffer(buffer: ByteBuffer): ByteBufferInputStream =
    new ByteBufferInputStream(buffer)

  private def fromBytes(bytes: Byte*): ByteBufferInputStream =
    fromByteBuffer(ByteBuffer.wrap(bytes.toArray))

  "ByteBufferInputStream" should {
    "report available bytes" in {
      forall(0 until 10) { i =>
        val bb = ByteBuffer.wrap(new Array[Byte](i))
        fromByteBuffer(bb).available() must_== i
      }
    }

    "read -1 when bytes unavailable" in {
      fromBytes().read() must_== -1
    }

    "read byte when available" in {
      val range = 0 until 10
      val is = fromBytes(range.map(_.toByte): _*)
      forall(range)(i => is.read() must_== i)

      is.available() must_== 0
      is.read() must_== -1
    }

    "handle mark and reset apporpriately" in {
      val is = fromBytes((0 until 10).map(_.toByte): _*)

      is.markSupported must beTrue

      is.read() must_== 0
      is.mark(1)
      is.available() must_== 9
      is.read() must_== 1
      is.available() must_== 8
      is.reset()
      is.available() must_== 9
      is.read() must_== 1
      is.available() must_== 8
    }
  }
}
