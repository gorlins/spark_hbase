/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package examples.pythonconverters

import scala.collection.JavaConversions._
import scala.util.parsing.json.JSONObject

import org.apache.spark.api.python.Converter
import org.apache.hadoop.hbase.client.{Put, Result}
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.KeyValue.Type
import org.apache.hadoop.hbase.CellUtil


/**
 * Implementation of [[org.apache.spark.api.python.Converter]] that converts all 
 * the records in an HBase Result to a String. In the String, it contains row, column,
 * qualifier, timestamp, type and value
 */

class HBaseResultToStringConverter extends Converter[Any, String]{
  override def convert(obj: Any): String = {
    import collection.JavaConverters._
    val result = obj.asInstanceOf[Result]                      
    val output = result.listCells.asScala.map(cell =>
        Map(
          "row" -> Bytes.toStringBinary(CellUtil.cloneFamily(cell)),
          "columnFamily" -> Bytes.toStringBinary(CellUtil.cloneFamily(cell)),
          "qualifier" -> Bytes.toStringBinary(CellUtil.cloneQualifier(cell)),
          "timestamp" -> cell.getTimestamp.toString,
          "type" -> Type.codeToType(cell.getTypeByte).toString,
          "value" -> Bytes.toStringBinary(CellUtil.cloneValue(cell))
        )
      )
    // output is an instance of Map which will be translated to json String
    // Hbase will escape "\n", so it is safe to use "\n" to join json.
    output.map(JSONObject(_).toString()).mkString("\n")                                           
  }
}

/**
 * Implementation of [[org.apache.spark.api.python.Converter]] that converts all 
 * the records in an HBase Result to a String. In the String, it contains row, column,
 * qualifier, timestamp, type and value, all packed as JSON
 */
class HBaseResultToJSONConverter extends Converter[Any, String]{
  override def convert(obj: Any): String = {
    import collection.JavaConverters._
    val result = obj.asInstanceOf[Result]
    val output = result.listCells.asScala.map(cell =>
        Map(
          "row" -> Bytes.toStringBinary(CellUtil.cloneFamily(cell)),
          "columnFamily" -> Bytes.toStringBinary(CellUtil.cloneFamily(cell)),
          "qualifier" -> Bytes.toStringBinary(CellUtil.cloneQualifier(cell)),
          "timestamp" -> cell.getTimestamp.toString,
          "type" -> Type.codeToType(cell.getTypeByte).toString,
          "value" -> Bytes.toStringBinary(CellUtil.cloneValue(cell))
        )
      )
    // output is a JSON array of objects (maps)
    "[" + output.map(JSONObject(_).toString()).mkString(", ") + "]"
  }
}

/**
 * Implementation of [[org.apache.spark.api.python.Converter]] that converts all 
 * the records in an HBase Result into a list of maps, each containing row, column,
 * qualifier, timestamp, type and value.  Values are returned as raw bytes rather than as
 * printable versions, and thus may require unpacking.
 */
class HBaseResultToListConverter extends Converter[Any, java.util.List[java.util.Map[String, String]]]{
  override def convert(obj: Any): java.util.List[java.util.Map[String, String]] = {
    import collection.JavaConverters._
    val result = obj.asInstanceOf[Result]
    val output = result.listCells.asScala.map(cell =>
        new java.util.HashMap[String, String](Map(
          "row" -> Bytes.toString(CellUtil.cloneFamily(cell)),
          "columnFamily" -> Bytes.toString(CellUtil.cloneFamily(cell)),
          "qualifier" -> Bytes.toString(CellUtil.cloneQualifier(cell)),
          "timestamp" -> cell.getTimestamp.toString,
          "type" -> Type.codeToType(cell.getTypeByte).toString,
          "value" -> Bytes.toString(CellUtil.cloneValue(cell))
        ).asJava)
      )
    new java.util.ArrayList[java.util.Map[String, String]](output.asJava)
  }
}

/**
 * Implementation of [[org.apache.spark.api.python.Converter]] that converts all 
 * the records in an HBase Result into a Map of "columnFamily:column"->"value" consistent
 * with a Python dict.
 * 
 * Only works with 1 version max (possibly latest if multiple are returned?).  
 * Ser/deser as python dict naturally, via HashMap.
 *
 * Values are returned as raw bytes rather than as printable versions, and thus
 * may require unpacking.
 */
class HBaseResultToMapConverter extends Converter[Any, java.util.Map[String, String]]{
  override def convert(obj: Any): java.util.Map[String, String] = {

    import collection.JavaConverters._
    val result = obj.asInstanceOf[Result]

    val my_map = result.listCells.asScala.map(
        cell => (
            Bytes.toString(CellUtil.cloneFamily(cell)) + ":" +
            Bytes.toString(CellUtil.cloneQualifier(cell)),
            Bytes.toString(CellUtil.cloneValue(cell)))
    ).toMap.asJava

    // nb: java.util.Map and scala map do not serialize into python??
    new java.util.HashMap[String, String](my_map)
  }
}

/**
 * Returns the most recent cell timestamp on the row as a long integer, useful for 
 * operations that require syncing to data changes.
 */
class MaxHBaseTimestamp extends Converter[Any, Long]{
  override def convert(obj: Any): Long = {
    import collection.JavaConverters._
    val result = obj.asInstanceOf[Result]
    result.listCells.asScala.map(cell =>
        cell.getTimestamp
      ).max
  }
}

/**
 * Implementation of [[org.apache.spark.api.python.Converter]] that converts an
 * ImmutableBytesWritable to a String
 */
class ImmutableBytesWritableToStringConverter extends Converter[Any, String] {
  override def convert(obj: Any): String = {
    val key = obj.asInstanceOf[ImmutableBytesWritable]
    Bytes.toStringBinary(key.get())
  }
}
