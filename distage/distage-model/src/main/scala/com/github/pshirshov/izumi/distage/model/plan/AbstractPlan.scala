package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ImportDependency
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp.{CallProvider, ReferenceInstance}
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.UnaryWiring
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.UnaryWiring.Instance
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.functional.Renderable

sealed trait AbstractPlan {

  def definition: ModuleBase
  def steps: Seq[ExecutableOp]

  final lazy val index: Map[DIKey, ExecutableOp] = {
    steps.map(s => s.target -> s).toMap
  }

  /** Get all imports (unresolved dependencies).
    *
    * Note, presence of imports doesn't automatically mean that a plan is invalid,
    * Imports may be fulfilled by a `Locator`, by BootstrapContext, or they may be materialized by a custom
    * [[com.github.pshirshov.izumi.distage.model.provisioning.strategies.ImportStrategy]]
    **/
  final def getImports: Seq[ImportDependency] =
    steps.collect { case i: ImportDependency => i }

  final def resolveImportsOp(f: PartialFunction[ImportDependency, Seq[ExecutableOp]]): SemiPlan = {
    SemiPlan(definition, steps = AbstractPlan.resolveImports(f, steps.toVector))
  }

  def resolveImports(f: PartialFunction[ImportDependency, Any]): AbstractPlan

  def resolveImport[T: Tag](instance: T): AbstractPlan

  def resolveImport[T: Tag](id: String)(instance: T): AbstractPlan

  def locateImports(locator: Locator): AbstractPlan

  final def providerImport[T](f: ProviderMagnet[T]): SemiPlan = {
    resolveImportsOp {
      case i if i.target.tpe == f.get.ret =>
        Seq(CallProvider(i.target, UnaryWiring.Function(f.get, f.get.associations), i.origin))
    }
  }

  final def providerImport[T](id: String)(f: ProviderMagnet[T]): SemiPlan = {
    resolveImportsOp {
      case i if i.target == DIKey.IdKey(f.get.ret, id) =>
        Seq(CallProvider(i.target, UnaryWiring.Function(f.get, f.get.associations), i.origin))
    }
  }

  final def map(f: ExecutableOp => ExecutableOp): SemiPlan = {
    SemiPlan(definition, steps.map(f).toVector)
  }

  final def flatMap(f: ExecutableOp => Seq[ExecutableOp]): SemiPlan = {
    SemiPlan(definition, steps.flatMap(f).toVector)
  }

  final def collect(f: PartialFunction[ExecutableOp, ExecutableOp]): SemiPlan = {
    SemiPlan(definition, steps.collect(f).toVector)
  }

  final def ++(that: AbstractPlan): SemiPlan = {
    SemiPlan(definition ++ that.definition, steps.toVector ++ that.steps)
  }

  final def foldLeft[T](z: T, f: (T, ExecutableOp) => T): T = {
    steps.foldLeft(z)(f)
  }

  override def toString: String = {
    steps.map(_.format).mkString("\n")
  }
}

object AbstractPlan {
  private[plan] def resolveImports(f: PartialFunction[ImportDependency, Seq[ExecutableOp]], steps: Vector[ExecutableOp]): Vector[ExecutableOp] =
    steps.flatMap {
      case i: ImportDependency =>
        f.lift(i) getOrElse Seq(i)
      case op =>
        Seq(op)
    }

  private[plan] def importToInstances(f: PartialFunction[ImportDependency, Any]): PartialFunction[ImportDependency, Seq[ExecutableOp]] =
    Function.unlift(i => f.lift(i).map(instance => Seq(ReferenceInstance(i.target, Instance(i.target.tpe, instance), i.origin))))
}

/** Unordered plan. You can turn into an [[OrderedPlan]] by using [[com.github.pshirshov.izumi.distage.model.Planner#finish]] **/
final case class SemiPlan(definition: ModuleBase, steps: Vector[ExecutableOp]) extends AbstractPlan {

  override def resolveImport[T: Tag](instance: T): SemiPlan =
    resolveImports {
      case i if i.target == DIKey.get[T] =>
        instance
    }

  override def resolveImport[T: Tag](id: String)(instance: T): SemiPlan = {
    resolveImports {
      case i if i.target == DIKey.get[T].named(id) =>
        instance
    }
  }

  override def resolveImports(f: PartialFunction[ImportDependency, Any]): SemiPlan = {
    copy(steps = AbstractPlan.resolveImports(AbstractPlan.importToInstances(f), steps))
  }

  override def locateImports(locator: Locator): SemiPlan = {
    resolveImports(Function.unlift(i => locator.lookup[Any](i.target)))
  }

}

final case class OrderedPlan(definition: ModuleBase, steps: Vector[ExecutableOp], topology: PlanTopology) extends AbstractPlan {

  def render(implicit ev: Renderable[OrderedPlan]): String = ev.render(this)

  override def resolveImports(f: PartialFunction[ImportDependency, Any]): OrderedPlan = {
    copy(steps = AbstractPlan.resolveImports(AbstractPlan.importToInstances(f), steps))
  }

  override def resolveImport[T: Tag](instance: T): OrderedPlan =
    resolveImports {
      case i if i.target == DIKey.get[T] =>
        instance
    }

  override def resolveImport[T: Tag](id: String)(instance: T): OrderedPlan = {
    resolveImports {
      case i if i.target == DIKey.get[T].named(id) =>
        instance
    }
  }

  override def locateImports(locator: Locator): OrderedPlan = {
    resolveImports(Function.unlift(i => locator.lookup[Any](i.target)))
  }
}
