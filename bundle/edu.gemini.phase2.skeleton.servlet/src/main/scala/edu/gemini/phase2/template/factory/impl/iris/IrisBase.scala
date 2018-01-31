package edu.gemini.phase2.template.factory.impl.iris

import edu.gemini.phase2.template.factory.impl._
import edu.gemini.pot.sp.{ISPGroup, ISPObservation}
import scala.collection.JavaConverters._
import edu.gemini.spModel.gemini.iris.blueprint.SpIrisBlueprint
import edu.gemini.spModel.gemini.iris.IrisSeqConfig
import edu.gemini.spModel.gemini.iris.Iris.Filter

trait IrisBase extends GroupInitializer[SpIrisBlueprint] with TemplateDsl {

  val program = "IRIS PHASE I/II MAPPING BPS"
  val seqConfigCompType = IrisSeqConfig.SP_TYPE

  implicit def pimpInst(obs: ISPObservation) = new {

    val ed = ObservationEditor[edu.gemini.spModel.gemini.iris.Iris](obs, instrumentType, IrisSeqConfig.SP_TYPE)

    def setFilter(d: Filter): Either[String, Unit] =
      ed.updateInstrument(_.setFilter(d))

    def setFilters(lst: Iterable[Filter]): Either[String, Unit] = for {
      _ <- lst.headOption.toRight("One or more filters must be specified.").right
      _ <- setFilter(lst.head).right
      _ <- ed.iterateFirst(edu.gemini.spModel.gemini.iris.Iris.FILTER_PROP.getName, lst.toList).right
    } yield ()

    def filter: Either[String, Filter] = ed.instrumentDataObject.right.map(_.getFilter)
  }

  // Local imports


  // HACK: override superclass initialize to hang onto db reference
  var db: Option[TemplateDb] = None

  override def initialize(db: TemplateDb): Maybe[ISPGroup] =
    try {
      this.db = Some(db)
      super.initialize(db)
    } finally {
      this.db = None
    }

  def attempt[A](a: => A) = tryFold(a) {
    e =>
      e.printStackTrace()
      e.getMessage
  }

  // DSL Setters
  def setFilter = Setter[Filter](sys.error("None"))(_.setFilter(_))

  def setFilters = Setter(blueprint.filters.asScala)(_.setFilters(_))
}
