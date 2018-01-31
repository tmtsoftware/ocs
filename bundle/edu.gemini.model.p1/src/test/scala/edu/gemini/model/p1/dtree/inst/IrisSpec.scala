package edu.gemini.model.p1.dtree.inst

import org.specs2.mutable.Specification

class IrisSpec extends Specification {
  "The Iris decision tree" should {
      "includes Iris filters" in {
        val iris = Iris()
        iris.title must beEqualTo("Filters")
        iris.choices must have size(22)
      }
  }

}
