/*
Copyright (c) 2011-2013 Robby, Kansas State University.        
All rights reserved. This program and the accompanying materials      
are made available under the terms of the Eclipse Public License v1.0 
which accompanies this distribution, and is available at              
http://www.eclipse.org/legal/epl-v10.html                             
*/

package org.sireum.test.framework

import org.scalatest._
import org.scalatest.junit._

import org.sireum.util._

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait TestFramework
    extends FunSuite with Matchers with AssertionsForJUnit with Assertions
    with ImplicitLogging {
  private type T = org.scalatest.Tag

  private var isSingle = false
  private var _casePrefix = ""
  private val tagsToInclude = marrayEmpty[String]

  def Single : this.type = {
    isSingle = true
    tagsToInclude += SINGLE_TAG_NAME
    this
  }

  def Case(s : String) : this.type = {
    this.synchronized {
      _casePrefix = s
    }
    this
  }

  def caseString : String = {
    this.synchronized {
      val r = if (_casePrefix != "") ("Case " + _casePrefix + ": ") else ""
      _casePrefix = ""
      return r
    }
  }

  protected override def test(testName : String, testTags : T*)(f : => Unit) {
    val tags = if (isSingle) testTags.+:(SingleTestTag) else testTags
    isSingle = false

    super.test(testName, tags : _*)(f)
  }

  override def run(testName : Option[String], args : Args) : Status = {
    val f = Filter(args.filter.tagsToInclude match {
      case Some(s) => Some(s ++ tagsToInclude)
      case _       => if (tagsToInclude.isEmpty) None else Some(tagsToInclude.toSet)
    }, args.filter.tagsToExclude)
    super.run(testName,
      Args(args.reporter, args.stopper, f, args.configMap, args.distributor,
        args.tracker, args.chosenStyles,
        args.runTestInNewInstance,
        args.distributedTestSorter,
        args.distributedSuiteSorter))
  }

  private object SingleTestTag extends T(SINGLE_TAG_NAME)

  private val SINGLE_TAG_NAME = "Single"

  import java.io._

  def checkRegression(file : File, dir : File, ext : String,
                      result : String, force : Boolean) {
    val filename = file.getName + "." + ext
    val expectedFileDir = new File(dir, "expected")
    val expectedFile = new File(expectedFileDir, filename)
    if (force || !expectedFile.exists) {
      expectedFileDir.mkdirs
      val fw = new FileWriter(expectedFile)
      fw.write(result)
      fw.close
    } else {
      val resultFileDir = new File(dir, "result")
      resultFileDir.mkdirs
      val resultFile = new File(resultFileDir, filename)
      val fw = new FileWriter(resultFile)
      fw.write(result)
      fw.close
      
      val resultLines = StringUtil.readLines(result)
      val expectedLines = FileUtil.readFileLines(FileUtil.toUri(expectedFile))._1
      resultLines should equal(expectedLines)
    }
  }
}