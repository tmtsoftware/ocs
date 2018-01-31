package edu.gemini.model.p1.immutable

import edu.gemini.model.p1.{mutable => M}

import scala.collection.JavaConverters._


object IrisBlueprint {
  def apply(m: M.IrisBlueprint): IrisBlueprint = new IrisBlueprint(m)
}

case class IrisBlueprint(filters: List[IrisFilter]) extends GeminiBlueprintBase {
  def name: String = "IRIS %s".format(filters.map(_.value).mkString("+"))

  def this(m: M.IrisBlueprint) = this(
    m.getFilter.asScala.toList
  )

  override def instrument: Instrument = Instrument.Iris
  override def ao: AoPerspective = AoLgs

  def mutable(n: Namer) = {
    val m = Factory.createIrisBlueprint()
    m.setId(n.nameOf(this))
    m.setName(name)
    m.getFilter.addAll(filters.asJava)
    m
  }

  def toChoice(n: Namer) = {
    val m = Factory.createIrisBlueprintChoice()
    m.setIris(mutable(n))
    m
  }

}