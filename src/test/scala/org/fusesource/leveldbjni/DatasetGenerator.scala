package org.fusesource.leveldbjni

import org.scalacheck.{Arbitrary, Gen}

trait DatasetGenerator {
  import Gen._

  implicit object arrayByteOrdering extends Ordering[Array[Byte]] {
    def compare(a1: Array[Byte], a2: Array[Byte]) = {
      var i = 0
      var result = 0
      while (i < a1.length && i < a2.length && result == 0) {
        result = (a1(i) & 0xff) - (a2(i) & 0xff) // LevelDB default comparator is on *unsigned* bytes
        i += 1
      }
      val ret = if (result == 0) {
        a1.length - a2.length
      } else {
        result
      }
      //println("  result = " + ret)
      ret
    }
  }

  val alphaNumString = sized(size => listOfN(size, alphaNumChar).map(_.mkString("")))

  def arbBytes(): Gen[Array[Byte]] = sized(size => arbBytes(size))

  def arbBytes(size: Int): Gen[Array[Byte]] = listOfN(size, Arbitrary.arbByte.arbitrary).map(_.toArray)

  def createDataset(size: Int, keyGen: Gen[Array[Byte]], valGen: Gen[Array[Byte]]): List[(Array[Byte],Array[Byte])] = {    
    val (keys,vals) = (listOfN(size, keyGen).sample.get, listOfN(size, valGen).sample.get)

    // Need to make sure keys are distinct, since leveldb will overwrite duplicates
    keys.map(_.toList).distinct.map(_.toArray) zip vals
  }
}
