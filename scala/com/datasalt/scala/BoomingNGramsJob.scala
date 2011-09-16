package com.datasalt.scala

import java.io.DataInput
import java.io.DataOutput
import scala.collection.JavaConversions.asScalaIterator
import scala.math.abs
import scala.math.pow
import scala.math.sqrt
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.io.WritableComparable
import org.apache.hadoop.io.IntWritable
import org.apache.hadoop.io.LongWritable
import org.apache.hadoop.io.Text
import org.apache.hadoop.io.Writable
import com.asimma.ScalaHadoop.ImplicitConversion.IntFromString
import com.asimma.ScalaHadoop.ImplicitConversion.IntWritableBox
import com.asimma.ScalaHadoop.ImplicitConversion.PathBox
import com.asimma.ScalaHadoop.ImplicitConversion.TextBox
import com.asimma.ScalaHadoop.ImplicitConversion.TextUnbox
import com.asimma.ScalaHadoop.TypedMapper
import com.asimma.ScalaHadoop.TypedReducer
import com.asimma.ScalaHadoop.IO
import com.asimma.ScalaHadoop.MapReduceTask
import com.asimma.ScalaHadoop.MapReduceTaskChain
import com.asimma.ScalaHadoop.ScalaHadoopTool
import org.apache.hadoop.conf.Configuration
import com.datasalt.scala.TextGroupingComparatorBase

/**
 * This Job is a small experiment that uses ScalaHadoop to crunch the Google N-Grams, trying to find words
 * whose importance boomed significantly in a period of 5 or less years.
 * 
 */

/*
 * A Writable Int Pair that leverages Scala's concise coding style 
 */
class IntPair(var i1: Int, var i2: Int) extends Writable {
  
  def this() = this(0, 0)
  
  override def readFields(in: DataInput) : Unit = {
    i1 = in readInt()
    i2 = in readInt()
  }
  override def write(out: DataOutput) : Unit = {
    out writeInt(i1)
    out writeInt(i2)
  }
}

/*
 * A grouping comparator that compares the first word of a pair of texts divided by \t
 */
class GroupByFirstWord extends TextGroupingComparatorBase {
  
  override def compare(a: WritableComparable[_], b: WritableComparable[_]): Int = {
    return a.asInstanceOf[Text].toString.split("\t")(0) compareTo b.asInstanceOf[Text].toString.split("\t")(0)
  }
}

/*
 * Mapper that emits the ngram as Key and the (year + count) as Value.
 * We add the year to the key to secondary sort by it.
 */
object SplitMap extends TypedMapper[LongWritable, Text, Text, IntPair] {
  override def map(k: LongWritable, v: Text, context: ContextType) : Unit = {
    val line = v split "\t"
    context write( line(0) + "\t" + line(1), new IntPair( line(1), line(2) )) // ngram => year, count
  }
}

/*
 * Reducer that tries to find outliers in 5-years count differences. It reads the ngram-totals.txt file from
 * the relative "/" source path.
 */
class FiveYearsDifferenceReducer extends TypedReducer[Text, IntPair, Text, IntWritable] {

  val totalsTable = collection.mutable.Map[Int, Int]()

  override def setup(context: ContextType): Unit = {
    // read "totals" table
    for (line <- io.Source.fromInputStream(getClass.getResourceAsStream("/ngram-totals.txt")).getLines) {
      val tokens = line.split("\t")
      totalsTable += (Integer.parseInt(tokens(0)) -> Integer.parseInt(tokens(3)))
    }
  }

  override def reduce(k: Text, v: java.lang.Iterable[IntPair], context: ContextType): Unit = {
    context getCounter ("stats", "ngroups") increment 1
    val key = (k split "\t")(0)
    // Normalize the counts according to the total books in the total counts table
    val list = v.iterator.map { b =>
      {
        val normalized: Double = b.i2 / totalsTable(b.i1).asInstanceOf[Double];
        (b.i1, normalized)
      }
    }.toList
    // Calculate differences between 5 years window
    val yearDiff = 5
    val list2 = list.map {
      var s = 0; b => {
        val prev = if (s >= yearDiff) list(s - yearDiff)._2 else 0;
        var ret = b._2 - prev;
        s = s + 1;
        (b._1, b._2, ret)
      }
    }.toArray
    // Calculate the mean and the standard deviation of the 5 year differences
    val diffList = list2.map(_._3)
    val avgDiff = diffList.sum / list.length.asInstanceOf[Double]
    val stdDev = sqrt(diffList.foldLeft(0.0)(_ + sqdDiff(_, avgDiff)) / list.length.toDouble)
    // Return a filtered function that contains (positive) statistical outliers
    // We'll filter by year > 1900 to avoid some weird outliers in very old book
    val (entrList, _) = list2.partition(
      b => { 
      	var (year, _, diff) = b;
      	abs(diff) > (avgDiff + 5 * stdDev) && year > 1900; 
     });
    // If there is only one singularity and it is + positive, then we're done
    if (entrList.length == 1 && entrList(0)._3 > 0) {
      val (year, count, dff) = entrList(0)
      println(key + " " + year + " " + count + " " + dff)
      context.write(key, year)
    }
  }

  def sqdDiff(value1: Double, value2: Double) = { val diff = value1 - value2; pow(diff, 2.0); }
}

/**
 * This class has to be called with input file as first argument and output path as second.
 */
object BoomingNGramsJob extends ScalaHadoopTool {
  def run(args: Array[String]) : Int = {
  	val conf = new Configuration
  	// inputFile -> variable with user's input file
  	val inputFile = args(0)
    // outputPath -> variable with user's output path
  	val outputPath = args(1)
    val mapReduce = MapReduceTaskChain.init(conf) -->
       IO.Text[LongWritable, Text](inputFile).input -->  
       MapReduceTask.MapReduceTask(SplitMap, new FiveYearsDifferenceReducer()) -->
       IO.Text[Text, IntWritable](outputPath).output;    
    val job = mapReduce.getJob
    // this is very ugly, if anybody knows how to do this prettier please tell me:
    val cl = new GroupByFirstWord
    val clazz = cl.getClass
    job setGroupingComparatorClass clazz.asInstanceOf[java.lang.Class[org.apache.hadoop.io.RawComparator[_]]]
    job waitForCompletion(true)
    return 0;
  }
}
