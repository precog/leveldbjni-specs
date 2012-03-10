package org.fusesource.leveldbjni

import scala.annotation.tailrec

import java.io.File
import java.nio.ByteBuffer

import com.weiglewilczek.slf4s.Logging

import org.iq80.leveldb._

import org.fusesource.leveldbjni.internal.JniDBIterator
import org.fusesource.leveldbjni.KeyValueChunk.KeyValuePair

import org.specs2.ScalaCheck
import org.specs2.specification.Scope
import org.specs2.mutable.{After, Specification}

import org.scalacheck.{Arbitrary,Gen}

class LevelDBJNISpec extends Specification with ScalaCheck with Logging with DatasetGenerator {
  val factory = JniDBFactory.factory

  // Handle some setup/teardown of the database files automatically
  trait columnSetup extends Scope with After {
    val dataDir = File.createTempFile("LevelDBJNISpec", ".db")
    logger.info("Using %s as base directory".format(dataDir))

    val DB = {
      dataDir.delete() // Ugly, but it works
      
      factory.open(dataDir, (new Options).createIfMissing(true))
    }

    def after = {
      DB.close()

      // Here we need to remove the entire directory and contents
      def delDir (dir : File) {
        dir.listFiles.foreach {
          case d if d.isDirectory => delDir(d)
          case f => f.delete()
        }
        dir.delete()
      }
      delDir(dataDir)
    }
  }

  @tailrec private final def compareElements(toCompare: List[(Array[Byte],Array[Byte])], iter: DBIterator) : Boolean = toCompare match {
    case x :: xs => {
      iter.hasNext must_== true

      val pair = iter.next

      pair.getKey.toList must_== x._1.toList
      pair.getValue.toList must_== x._2.toList

      compareElements(xs, iter)
    }
    case _ => true
  }
  
  @tailrec private final def compareChunks(toCompare: List[(Array[Byte],Array[Byte])],
                                           iter: JniDBIterator,
                                           chunkIter: java.util.Iterator[KeyValuePair],
                                           backing: ByteBuffer,
                                           keyEncoding: DataWidth,
                                           valEncoding: DataWidth) : Boolean = toCompare match {
    case x :: xs if chunkIter.hasNext => {
      val pair = chunkIter.next

      pair.getKey.toList must_== x._1.toList
      pair.getValue.toList must_== x._2.toList

      compareChunks(xs, iter, chunkIter, backing, keyEncoding, valEncoding)
    }
    case x :: xs => {
      iter.hasNext must_== true
      compareChunks(toCompare, iter, iter.nextChunk(backing, keyEncoding, valEncoding).getIterator, backing, keyEncoding, valEncoding)
    }
    case _ => true
  }

  def runChunkExample(keySize: Option[Int], valSize: Option[Int]): Boolean = {
    val scope = new columnSetup() {}

    try {
      val dataset = createDataset(sampleSize,
                                  keySize.map(arbBytes(_)).getOrElse(arbBytes()),
                                  valSize.map(arbBytes(_)).getOrElse(arbBytes()))

      dataset foreach {
        case (k,v) => scope.DB.put(k, v)
      }

      val sorted = dataset.sorted

      val iter = scope.DB.iterator().asInstanceOf[JniDBIterator]
      iter.seekToFirst

      val backing = ByteBuffer.allocate(1000)
      
      val firstChunk = iter.nextChunk(backing,
                                      keySize.map(DataWidth.FIXED(_)).getOrElse(DataWidth.VARIABLE),
                                      valSize.map(DataWidth.FIXED(_)).getOrElse(DataWidth.VARIABLE))

      compareChunks(sorted,
                    iter,
                    firstChunk.getIterator,
                    backing,
                    keySize.map(DataWidth.FIXED(_)).getOrElse(DataWidth.VARIABLE),
                    valSize.map(DataWidth.FIXED(_)).getOrElse(DataWidth.VARIABLE))
    } finally {
      scope.after
    }
  }

  val sampleSize = 1000

  val minCheckSize = 100

  override val defaultValues = super.defaultValues + (minSize -> minCheckSize)

  "LevelDBJNI" should {
    
    "Perform base operations" in {
      "by inserting properly and in sorted order" in new columnSetup {
        val dataset = createDataset(sampleSize, arbBytes(12), arbBytes(12))
        
        dataset foreach {
          case (k,v) => DB.put(k, v)
        }

        val sorted = dataset.sorted

        val iter = DB.iterator()
        iter.seekToFirst
        
        compareElements(sorted, iter)
        println("Completed insertion spec")
      }
    }

    case class DataSize(size: Int)

    implicit val arbDataSize = Arbitrary(Gen.choose(0,100).map(DataSize(_)))

    "Correctly process chunks" in {

      "with variable key and value encoding" in new columnSetup {
        (1 to minCheckSize).foreach { // Make sure to run at least as many times as the rest of the checks
          _ => runChunkExample(None, None)
        }
      }

      "with variable key and fixed value encodings" in {
        check { (size: DataSize) => runChunkExample(None, Some(size.size + 1)) }
      }

      "with fixed key and variable value encodings" in {
        check { (size: DataSize) => runChunkExample(Some(size.size + 1), None) }
      }

      "with fixed key and variable value encodings" in {
        check { (keySize: DataSize, valSize: DataSize) => runChunkExample(Some(keySize.size + 1), Some(valSize.size + 1)) }
      }

      "with zero-width values" in {
        check { (size: DataSize) => runChunkExample(None, Some(0)) }
      }
    }
  }
}
