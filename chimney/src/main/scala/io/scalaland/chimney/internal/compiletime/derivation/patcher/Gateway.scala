package io.scalaland.chimney.internal.compiletime.derivation.patcher

import io.scalaland.chimney.dsl.PatcherDefinitionCommons
import io.scalaland.chimney.Patcher
import io.scalaland.chimney.internal.compiletime.DerivationResult
import io.scalaland.chimney.internal.compiletime.derivation.GatewayCommons
import io.scalaland.chimney.internal.runtime

private[compiletime] trait Gateway extends GatewayCommons { this: Derivation =>

  import ChimneyType.Implicits.*

  final def derivePatcherResult[
      A: Type,
      Patch: Type,
      Overrides <: runtime.PatcherOverrides: Type,
      Flags <: runtime.PatcherFlags: Type,
      ImplicitScopeFlags <: runtime.PatcherFlags: Type
  ](
      obj: Expr[A],
      patch: Expr[Patch],
      runtimeDataStore: Expr[PatcherDefinitionCommons.RuntimeDataStore]
  ): Expr[A] = suppressWarnings {
    cacheDefinition(runtimeDataStore) { runtimeDataStore =>
      cacheDefinition(obj) { obj =>
        cacheDefinition(patch) { patch =>
          val context = PatcherContext
            .create[A, Patch](
              obj,
              patch,
              config =
                PatcherConfigurations.readPatcherConfiguration[Overrides, Flags, ImplicitScopeFlags](runtimeDataStore)
            )
            .updateConfig(_.allowAPatchImplicitSearch)

          val result = enableLoggingIfFlagEnabled(derivePatcherResultExpr(context), context)

          Expr.block(
            List(Expr.suppressUnused(runtimeDataStore), Expr.suppressUnused(obj), Expr.suppressUnused(patch)),
            extractExprAndLog[A, Patch, A](result)
          )
        }
      }
    }
  }

  final def derivePatcher[
      A: Type,
      Patch: Type,
      Overrides <: runtime.PatcherOverrides: Type,
      Flags <: runtime.PatcherFlags: Type,
      ImplicitScopeFlags <: runtime.PatcherFlags: Type
  ](
      runtimeDataStore: Expr[PatcherDefinitionCommons.RuntimeDataStore]
  ): Expr[Patcher[A, Patch]] = suppressWarnings {
    cacheDefinition(runtimeDataStore) { runtimeDataStore =>
      val result = DerivationResult.direct[Expr[A], Expr[Patcher[A, Patch]]] { await =>
        ChimneyExpr.Patcher.instance[A, Patch] { (obj: Expr[A], patch: Expr[Patch]) =>
          val context = PatcherContext.create[A, Patch](
            obj,
            patch,
            config =
              PatcherConfigurations.readPatcherConfiguration[Overrides, Flags, ImplicitScopeFlags](runtimeDataStore)
          )

          await(enableLoggingIfFlagEnabled(derivePatcherResultExpr(context), context))
        }
      }

      Expr.block(
        List(Expr.suppressUnused(runtimeDataStore)),
        extractExprAndLog[A, Patch, Patcher[A, Patch]](result)
      )
    }
  }

  private def enableLoggingIfFlagEnabled[A](
      result: => DerivationResult[A],
      ctx: PatcherContext[?, ?]
  ): DerivationResult[A] =
    enableLoggingIfFlagEnabled[A](
      DerivationResult.catchFatalErrors(result),
      ctx.config.flags.displayMacrosLogging,
      ctx.derivationStartedAt
    )

  private def extractExprAndLog[A: Type, Patch: Type, Out: Type](result: DerivationResult[Expr[Out]]): Expr[Out] =
    extractExprAndLog[Out](
      result,
      s"""Chimney can't derive patching for ${Type.prettyPrint[A]} with patch type ${Type.prettyPrint[Patch]}"""
    )
}
