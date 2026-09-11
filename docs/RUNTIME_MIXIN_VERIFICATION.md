# Verifying the mixins at runtime

`MixinTargetsTest` proves every injection target *exists* in the 26.2 jar, which is what CI can check
without a display. It cannot prove the injections are actually **applied** by Mixin when the game
runs. This document records how that second check was done, and how to repeat it.

## Why this is a separate check

A `@Inject` whose target method exists can still fail to apply: a bad `@At` slice, a mismatched
descriptor, a `compatibilityLevel` the subsystem rejects, or a target class that is simply never
loaded. `agalarhack.mixins.json` uses `required: true` with `defaultRequire: 1`, so an injection that
cannot be applied aborts class load rather than failing quietly — but only once something loads the
target class. Until the game runs, nothing does.

## Procedure

The client runs headless under `xvfb-run` with Mesa's software rasteriser. Minecraft 26.2 needs an
OpenGL 3.3 core context, so the overrides below are not optional; asking llvmpipe for 3.2 makes the
window creation fail with a misleading error.

```bash
export JAVA_HOME=/path/to/jdk-25
export MESA_GL_VERSION_OVERRIDE=4.5
export MESA_GLSL_VERSION_OVERRIDE=450
export GALLIUM_DRIVER=llvmpipe
export LIBGL_ALWAYS_SOFTWARE=1

# -Dmixin.debug.export=true writes every transformed class to run/.mixin.out/class
cat > /tmp/mixindbg.gradle <<'GRADLE'
gradle.projectsLoaded {
    rootProject {
        afterEvaluate {
            tasks.matching { it.name == 'runClient' }.configureEach {
                jvmArgs '-Dmixin.debug.export=true'
            }
        }
    }
}
GRADLE

rm -rf run/.mixin.out
timeout 300 xvfb-run -a --server-args="-screen 0 1280x720x24" \
    ./gradlew runClient --init-script /tmp/mixindbg.gradle \
    -Dorg.gradle.java.home=$JAVA_HOME
```

The client never reaches a playable state here — there is no audio device and no narrator library —
so it is killed by the timeout. That is fine: class transformation happens long before, and
`run/.mixin.out/class` is already written.

Then read the transformed bytecode back and look for the injected members:

```bash
for c in net/minecraft/client/multiplayer/ClientPacketListener \
         net/minecraft/client/renderer/GameRenderer \
         net/minecraft/world/entity/player/Player; do
    javap -p -c "run/.mixin.out/class/$c.class" | grep -o 'agalarhack[A-Za-z0-9_$./]*' | sort -u
done
```

Finding the handler method is not enough on its own — check that the **target method body calls it**,
which is what proves the `@At` resolved where it was meant to.

`run/` is already ignored, so `.mixin.out` never reaches a commit.

## Result on 26.2 (Fabric Loader 0.19.3, Fabric API 0.157.0+26.2)

Mixin 0.8.7 (sponge-mixin 0.17.3) accepted `Compatibility level set to JAVA_25`. The run reached
`Minecraft.runTick`, past the resource reload and texture atlas stitching, with **zero mixin
failures**. All three target classes were transformed, and all six injections were present and
invoked from the correct target method:

| Target | Method | Injected call |
| --- | --- | --- |
| `ClientPacketListener` | `handleBlockUpdate` | `handler$…$agalarhack$onBlockUpdate` |
| `ClientPacketListener` | `handleChunkBlocksUpdate` | `handler$…$agalarhack$onSectionBlocksUpdate` |
| `ClientPacketListener` | `handleEntityEvent` | `handler$…$agalarhack$onEntityEvent` |
| `ClientPacketListener` | `handleSetTime` | `handler$…$agalarhack$onSetTime` |
| `GameRenderer` | `bobHurt` | `agalarhack$skipHurtBob` |
| `Player` | `isStayingOnGroundSurface` | `agalarhack$holdEdge` (two call sites) |

`ClientPacketListener` was transformed even though the run never connected to a server, because the
class is loaded during startup rather than on connect. Its handlers were therefore verified as
applied, but never observed *firing* — that still needs a real connection.

## What this does and does not establish

It establishes that the bytecode the game runs contains our injections, in the right methods, on real
26.2. It establishes nothing about behaviour: nobody walked off a ledge to see `SafeWalk` hold the
edge, and nobody took damage to see `CameraTweaks` suppress the shake. The `UNTESTED` badge stays on
every module for that reason — it tracks in-game testing, not bytecode presence.
