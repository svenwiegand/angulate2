//     Project: angulate2 (https://github.com/jokade/angulate2)
// Description: Angular2 @Directive macro annotation

// Copyright (c) 2015 Johannes.Kastner <jokade@karchedon.de>
//               Distributed under the MIT License (see included LICENSE file)
package angulate2

import angulate2.impl.JsWhiteboxMacroTools

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import scala.scalajs.js

// NOTE: keep the constructor parameter list and Directive.Macro.annotationParamNames in sync!
@compileTimeOnly("enable macro paradise to expand macro annotations")
class Directive(selector: String,
                template: String = null,
                directives: js.Array[js.Any] = null,
                appInjector: js.Array[js.Any] = null) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro Directive.Macro.impl
}


object Directive {

  private[angulate2] class Macro(val c: whitebox.Context) extends JsWhiteboxMacroTools {

    import c.universe._

    lazy val debug = isSet("angulate2.debug.Directive")

    val annotationParamNames =
      Seq("selector")

    def impl(annottees: c.Expr[Any]*): c.Expr[Any] = annottees.map(_.tree).toList match {
      case (classDecl: ClassDef) :: Nil => modifiedDeclaration(classDecl)
      case _ => c.abort(c.enclosingPosition, "Invalid annottee for @Directive")
    }


    def modifiedDeclaration(classDecl: ClassDef) = {
      val parts = extractClassParts(classDecl)
      import parts._

      val annots = annotations( extractAnnotationParameters(c.prefix.tree,annotationParamNames) )

      // we use this dummy class only for type checking, so that we can access the types of the constructor parameters
      val cl = (q"""class $name ( ..$params )""").duplicate
      val diTypes = constructorDI(cl)

      val objName = fullName+"_"

      val jsAnnot = s"$fullName.annotations = $objName().annotations(); $fullName.parameters = $objName().parameters();"
      //val jsAnnot = s"$fullName.annotations = $objName().annotations();"

      val tree =
        q"""{@scalajs.js.annotation.JSExport($fullName)
             @scalajs.js.annotation.JSExportAll
             @de.surfice.sjsannots.SJSAnnotation(1000,$jsAnnot)
             class $name ( ..$params ) extends ..$parents { ..$body }
             @scalajs.js.annotation.JSExport($objName)
             object ${name.toTermName} {
               import angulate2.annotations._
               @scalajs.js.annotation.JSExport
               def annotations() = $annots
               @scalajs.js.annotation.JSExport
               def parameters() = $diTypes
             }
            }"""

      if(debug) printTree(tree)

      c.Expr[Any](tree)
    }


    def annotations(params: Map[String,Option[Tree]]) = {
      val groups = params.collect {
        case ("appInjector",Some(rhs)) => ("injectables",c.typecheck(rhs))
        case (name, Some(rhs)) => (name, rhs)
      }.groupBy {
        case ("selector", _) => "DirectiveAnnotation"
        case ("template"|"directives", _) => "ViewAnnotation"
        case _ => ???
      }.map{
        case (atype,m) =>
          val annot = TermName(atype)
          val params = m.toList.map( p => q"${TermName(p._1)} = ${p._2}")
          q"$annot( ..$params )"
      }

      q"scalajs.js.Array( ..$groups )"
    }


    def constructorDI(classDecl: Tree) = {
      val paramTypes = c.typecheck(classDecl) match {
        case q"class $_ ( ..$params )" => params.map {
          case q"$_ val $_: $tpe" => q"scalajs.js.Array(${selectGlobalDynamic(tpe.toString)})"
        }
      }
      q"scalajs.js.Array( ..$paramTypes )"
    }

  }

}

