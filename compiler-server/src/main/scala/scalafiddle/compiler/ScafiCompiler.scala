package scalafiddle.compiler

import scalafiddle.shared.ExtLib

import scala.util.{Failure, Success, Try}

object ScafiCompiler {
  val web                          = "scafi-web%%%scafi-web%0.3.3+98-caeaf12c+20201230-1723"
  def fiddleDep(s: String): String = "$FiddleDependency " + s + ""
  val deps                         = ExtLib(web)
  val library                      = new LibraryManager(Seq(deps, ExtLib(web)))

  def compile(core: String): Try[String] = {
    val h    = '"'
    val code = s"""
        |// ${fiddleDep(web)}
        |//init
        |import java.util.concurrent.TimeUnit
        |import it.unibo.scafi.js.controller.local
        |import it.unibo.scafi.js.controller.scripting.Script.ScaFi
        |import it.unibo.scafi.js.controller.local._
        |import it.unibo.scafi.js.dsl.semantics._
        |import it.unibo.scafi.js.dsl.{ScafiInterpreterJs, WebIncarnation}
        |import it.unibo.scafi.js.utils.Execution
        |import it.unibo.scafi.js.view.dynamic._
        |import it.unibo.scafi.js.view.dynamic.graph.{LabelRender, PhaserGraphSection, PhaserInteraction}
        |import it.unibo.scafi.js.view.static.SkeletonPage
        |import it.unibo.scafi.js.Index
        |import scala.concurrent.duration.FiniteDuration
        |import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
        |@JSExportTopLevel("Injected")
        |object Injected {
        |   import org.scalajs.dom._
        |   implicit val incarnation = Index.incarnation //incarnation choosed
        |   import incarnation._ //allow scripting
        |   implicit val languageJsInterpreter = Index.languageJsInterpreter
        |   val configuration = Index.configuration
        |   val updateTime = Index.updateTime
        |   val support = Index.support
        |   @JSExport
        |   def main() : Unit = {
        |     implicit val context = Execution.timeoutBasedScheduler
        |     //page injection
        |     document.head.appendChild(SkeletonPage.renderedStyle.render)
        |     document.body.appendChild(SkeletonPage.content.render)
        |     //dynamic part configuration
        |     val visualizationSettingsSection = VisualizationSettingsSection(SkeletonPage.visualizationOptionDiv)
        |     val renders : Seq[LabelRender.LabelRender] = Seq(LabelRender.booleanRender, LabelRender.booleanExport, /*LabelRender.gradientLike, test only*/ LabelRender.textifyBitmap)
        |     val phaserRender = new PhaserGraphSection(SkeletonPage.visualizationSection, new PhaserInteraction(support), visualizationSettingsSection, renders)
        |     val configurationSection = new ConfigurationSection(SkeletonPage.backendConfig, support)
        |     val editor = new EditorSection(SkeletonPage.editorSection, SkeletonPage.selectionProgram, Index.programs)
        |     editor.editor.setValue($h$h$h$core$h$h$h)
        |     $core //inject code
        |     SimulationControlsSection.render(support, editor.editor, SkeletonPage.controlsDiv, Some(ScaFi(program)))
        |     //attach the simulator with the view
        |     support.graphStream.sample(FiniteDuration(updateTime, TimeUnit.MILLISECONDS)).foreach(phaserRender)
        |     //force repaint
        |     support.invalidate()
        |     SkeletonPage.visualizationSection.focus()
        |     EventBus.publish(configuration) //tell to all component the new configuration installed on the frontend
        |   }
        |}
        |""".stripMargin

    val compiler = new Compiler(library, code)
    val result   = compiler.compile(println)
    result match {
      case (a, Some(b)) =>
        val res = compiler.fastOpt(b)
        Success(compiler.`export`(res))
      case (a, _) => Failure(throw new IllegalArgumentException(a))
    }
  }
}
