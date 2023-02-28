package quotidian.syntax

import scala.annotation.tailrec
import scala.deriving.Mirror
import scala.quoted.*

// Extensions

extension (using Quotes)(self: quotes.reflect.Symbol.type)
  def of[A: Type]: quotes.reflect.Symbol =
    import quotes.reflect.*
    TypeTree.of[A].symbol

extension (using Quotes)(self: quotes.reflect.Term.type)
  def companionOf[A: Type]: quotes.reflect.Term =
    import quotes.reflect.*
    Ident(TypeRepr.of[A].typeSymbol.companionModule.termRef)

  def constructProduct[A: Type](args: List[quotes.reflect.Term]): quotes.reflect.Term =
    import quotes.reflect.*
    TypeRepr.of[A] match
      case AppliedType(t, targs) =>
        t.asType match
          case '[t] =>
            val companion = Term.companionOf[t]
//            val isTrueSingleton = Symbol.of[t].primaryConstructor.paramSymss.isEmpty
//            if isTrueSingleton then companion.appliedToTypes(targs)
//            else

            val res = companion.selectUnique("apply").appliedToTypes(targs).appliedToArgs(args)
//            report.errorAndAbort(s"res = ${res.show}, args = ${args}")
            res
      case _ =>
        val companion       = Term.companionOf[A]
        val isTrueSingleton = Symbol.of[A].primaryConstructor.paramSymss.isEmpty
        if isTrueSingleton then companion
        else companion.selectUnique("apply").appliedToArgs(args)

extension (using Quotes)(self: quotes.reflect.TypeRepr.type)

  def fieldTypes[A: Type]: List[quotes.reflect.TypeRepr] =
    Expr.summon[Mirror.ProductOf[A]].get match
      case '{ $p: Mirror.ProductOf[A] { type MirroredElemTypes = tpes } } =>
        quotes.reflect.TypeRepr.of[tpes].tupleToList

  def makeTuple(args: List[quotes.reflect.TypeRepr]): quotes.reflect.TypeRepr =
    import quotes.reflect.*
    val tupleCons = TypeRepr.typeConstructor[*:[?, ?]]
    args
      .foldRight(TypeRepr.of[EmptyTuple]) { (tpe, acc) =>
        tpe.asType match
          case '[t] =>
            AppliedType(tupleCons, List(TypeRepr.of[Expr[t]], acc))
      }

  def typeConstructor[A: Type]: quotes.reflect.TypeRepr =
    import quotes.reflect.*
    val typeRepr = TypeRepr.of[A]
    typeRepr match
      case UnderlyingTypeConstructor(t) => t
      case _                            => report.errorAndAbort(s"Expected a type constructor, but got ${typeRepr.show}")

extension (using Quotes)(self: quotes.reflect.Symbol)
  def returnType: quotes.reflect.TypeRepr =
    import quotes.reflect.*
    self.termRef.widenTermRefByName

  def isPublic: Boolean =
    import quotes.reflect.*
    !self.flags.is(Flags.Private) && !self.flags.is(Flags.Protected) &&
    !self.flags.is(Flags.Local) && !self.flags.is(Flags.Synthetic) &&
    !self.flags.is(Flags.Artifact) && !self.flags.is(Flags.Macro)

extension (using Quotes)(self: quotes.reflect.Term)
  def selectUnique(name: String): quotes.reflect.Term =
    import quotes.reflect.*
    Select.unique(self, name)

  def selectOverloaded(
      name: String,
      targs: List[quotes.reflect.TypeRepr],
      args: List[quotes.reflect.Term]
  ): quotes.reflect.Term =
    import quotes.reflect.*
    Select.overloaded(self, name, targs, args)

extension (using Quotes)(self: quotes.reflect.TypeRepr)
  def unapplied: quotes.reflect.TypeRepr =
    import quotes.reflect.*
    self match
      case AppliedType(t, _) => t.unapplied
      case _                 => self

  def isGeneric: Boolean =
    import quotes.reflect.*
    self.typeSymbol.isTypeParam

  def typeTree: quotes.reflect.TypeTree =
    import quotes.reflect.*
    self.asType match
      case '[t] => TypeTree.of[t]

  /** Turn a tuple of a TypeRepr into a List[TypeRepr]
    */
  def tupleToList: List[quotes.reflect.TypeRepr] =
    import quotes.reflect.*
    self.asType match
      case '[t *: ts]    => TypeRepr.of[t] :: TypeRepr.of[ts].tupleToList
      case '[EmptyTuple] => Nil

//extension [A](using Quotes)(self: Expr[A])

// Extractors

object Uninlined:
  @tailrec
  def unapply(using Quotes)(term: quotes.reflect.Term): Option[quotes.reflect.Term] =
    import quotes.reflect.*
    term match
      case Inlined(_, _, t) => Uninlined.unapply(t)
      case t                => Some(t)

object UnderlyingTypeConstructor:
  /** Extracts the underlying function term of a function application.
    */
  @tailrec
  def unapply(using Quotes)(term: quotes.reflect.TypeRepr): Option[quotes.reflect.TypeRepr] =
    import quotes.reflect.*
    term match
      case AppliedType(t, _) => UnderlyingTypeConstructor.unapply(t)
      case t                 => Some(t)

final class Field[Q <: Quotes & Singleton](using val quotes: Q)(
    val symbol: quotes.reflect.Symbol,
    val name: String,
    val returnType: quotes.reflect.TypeRepr
)

object Field:
  def forProduct[A: Type](using quotes: Quotes): List[Field[quotes.type]] =
    import quotes.reflect.*
    val fields     = TypeRepr.fieldTypes[A]
    val caseFields = TypeRepr.of[A].typeSymbol.caseFields
    fields.zip(caseFields).map { (tpe, sym) =>
      new Field[quotes.type](sym, sym.name, tpe)
    }
