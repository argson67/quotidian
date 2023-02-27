package quotidian

import scala.quoted.*

given [A](using fromExprA: FromExpr[A]): FromExpr[Option[A]] =
  new FromExpr[Option[A]]:
    def unapply(expr: Expr[Option[A]])(using Quotes): Option[Option[A]] =
      import quotes.reflect.*
      expr match
        case '{ None }       => Some(None)
        case '{ Some($a) }   => fromExprA.unapply(a.asInstanceOf[Expr[A]]).map(Some(_))
        case '{ Option($a) } => fromExprA.unapply(a.asInstanceOf[Expr[A]]).map(Some(_))
        case _               => None

given [A](using toExprA: ToExpr[A]): ToExpr[Option[A]] =
  new ToExpr[Option[A]]:
    def apply(a: Option[A])(using Quotes): Expr[Option[A]] =
      import quotes.reflect.*
      a match
        case None => '{ None }
        case Some(a) =>
          val exprA = toExprA(a).asInstanceOf[Expr[Any]]
          '{ Some(${ exprA }) }.asInstanceOf[Expr[Option[A]]]

given [E, A](using fromExprE: FromExpr[E], fromExprA: FromExpr[A]): FromExpr[Either[E, A]] =
  new FromExpr[Either[E, A]]:
    def unapply(expr: Expr[Either[E, A]])(using Quotes): Option[Either[E, A]] =
      import quotes.reflect.*
      expr match
        case '{ Left($e) }  => fromExprE.unapply(e.asInstanceOf[Expr[E]]).map(Left(_))
        case '{ Right($a) } => fromExprA.unapply(a.asInstanceOf[Expr[A]]).map(Right(_))
        case _              => None

extension (fromExpr: FromExpr.type) //
  inline def derived[A]: FromExpr[A] = ${ DeriveFromExpr.deriveImpl[A] }

extension (toExpr: ToExpr.type) //
  inline def derived[A]: ToExpr[A] = ${ DeriveToExpr.deriveImpl[A] }
