package edu.gemini.phase2.template.factory.impl.iris

import edu.gemini.spModel.gemini.iris.blueprint.SpIrisBlueprint

case class Iris(blueprint: SpIrisBlueprint) extends IrisBase {

  // Some groupings
  private var sci = Seq.empty[Int]

//  INCLUDE {1, 2, 3} IN target-specific Scheduling Group
//     SCI={1},{2},{3}

  include(1, 2, 3) in TargetGroup
    sci = Seq(1, 2, 3)

  // # Filters
  // SET FILTERS from PI into any IRIS ITERATOR that includes a Filter configuration
  forObs(sci: _*)(setFilters fromPI)
}
