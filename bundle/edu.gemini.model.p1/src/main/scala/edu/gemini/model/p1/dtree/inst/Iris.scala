package edu.gemini.model.p1.dtree.inst

import edu.gemini.model.p1.dtree._
import edu.gemini.model.p1.immutable._

object Iris {
  def apply() = new FilterNode

  class FilterNode extends MultiSelectNode[Unit, IrisFilter, IrisBlueprint](()) {
    val title = "Filters"
    val description = "Select a filter for your configuration."

    def choices: List[IrisFilter] = IrisFilter.values.toList

    def apply(fs: List[IrisFilter]) = Right(IrisBlueprint(fs))

    def unapply = {
      case b: IrisBlueprint => b.filters
    }
  }

}