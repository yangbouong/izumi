package izumi.distage.modules.support

import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.typeclass.BIO3InstancesModule
import izumi.functional.bio.retry.{Scheduler2, Scheduler3}
import izumi.functional.bio.{Async2, Async3, Fork2, Fork3, Local3, Primitives2, Primitives3, PrimitivesM2, PrimitivesM3, SyncSafe2, SyncSafe3, Temporal2, Temporal3, UnsafeRun2, UnsafeRun3}
import izumi.functional.quasi.*
import izumi.reflect.{Tag, TagK3}

/**
  * Any `BIO` effect type support for `distage` resources, effects, roles & tests.
  *
  * For all `F[-_, +_, +_]` with available `make[Async3[F]]`, `make[Temporal3[F]]` and `make[UnsafeRun3[F]]` bindings.
  *
  *  - Adds [[izumi.functional.quasi.QuasiIO]] instances to support using `F[-_, +_, +_]` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  *  - Adds [[izumi.functional.bio]] typeclass instances for `F[-_, +_, +_]`
  *
  * Depends on `make[Async3[F]]`, `make[Temporal3[F]]`, `make[Local3[F]]`, `make[Fork3[F]]`, `make[UnsafeRun3[F]]`
 *  Optional additions: `make[Primitives3[F]]`, `make[PrimitivesM3[F]]`, `make[Scheduler3[F]]`
  */
class AnyBIO3SupportModule[F[-_, +_, +_]: TagK3, R0: Tag] extends ModuleDef {
  // trifunctor bio instances
  include(BIO3InstancesModule[F])

  def bio2Module[R: Tag]: ModuleDef = new ModuleDef {
    // QuasiIO & bifunctor bio instances
    include(AnyBIO2SupportModule[F[R, +_, +_]])

    make[Async2[F[R, +_, +_]]].from {
      implicit F: Async3[F] => Async2[F[R, +_, +_]]
    }
    make[Temporal2[F[R, +_, +_]]].from {
      implicit F: Temporal3[F] => Temporal2[F[R, +_, +_]]
    }
    make[Fork2[F[R, +_, +_]]].from {
      implicit Fork: Fork3[F] => Fork2[F[R, +_, +_]]
    }
  }

  include(bio2Module[Any])
  if (!(Tag[R0] =:= Tag[Any])) {
    include(bio2Module[R0])
  }
}

object AnyBIO3SupportModule extends App with ModuleDef {
  @inline def apply[F[-_, +_, +_]: TagK3, R: Tag]: AnyBIO3SupportModule[F, R] = new AnyBIO3SupportModule[F, R]

  /**
    * Make [[AnyBIO3SupportModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[Fork3[F]]` and `make[Primitives3[F]]` are not required by [[AnyBIO3SupportModule]]
    * but are added for completeness
    */
  def withImplicits[F[-_, +_, +_]: TagK3: Async3: Temporal3: Local3: UnsafeRun3: Fork3: Primitives3: PrimitivesM3: Scheduler3, R: Tag]: ModuleDef =
    new ModuleDef {
      include(AnyBIO3SupportModule[F, R])

      addImplicit[Async3[F]]
      addImplicit[Temporal3[F]]
      addImplicit[Local3[F]]
      addImplicit[Fork3[F]]
      addImplicit[Primitives3[F]]
      addImplicit[PrimitivesM3[F]]
      addImplicit[UnsafeRun3[F]]
      addImplicit[Scheduler3[F]]

      // no corresponding bifunctor (`F[Any, +_, +_]`) instances need to be added for these types because they already match
      private[this] def aliasingCheck(): Unit = {
        lazy val _ = aliasingCheck()
        implicitly[Scheduler3[F] =:= Scheduler2[F[Any, +_, +_]]]
        implicitly[UnsafeRun3[F] =:= UnsafeRun2[F[Any, +_, +_]]]
        implicitly[Primitives3[F] =:= Primitives2[F[Any, +_, +_]]]
        implicitly[PrimitivesM3[F] =:= PrimitivesM2[F[Any, +_, +_]]]
        implicitly[SyncSafe3[F] =:= SyncSafe2[F[Any, +_, +_]]]
        implicitly[QuasiIORunner3[F] =:= QuasiIORunner2[F[Any, +_, +_]]]
        implicitly[QuasiFunctor3[F] =:= QuasiFunctor2[F[Any, +_, +_]]]
        implicitly[QuasiApplicative3[F] =:= QuasiApplicative2[F[Any, +_, +_]]]
        implicitly[QuasiPrimitives3[F] =:= QuasiPrimitives2[F[Any, +_, +_]]]
        implicitly[QuasiIO3[F] =:= QuasiIO2[F[Any, +_, +_]]]
        implicitly[QuasiAsync3[F] =:= QuasiAsync2[F[Any, +_, +_]]]
        implicitly[QuasiTemporal3[F] =:= QuasiTemporal2[F[Any, +_, +_]]]
        ()
      }
    }
}
