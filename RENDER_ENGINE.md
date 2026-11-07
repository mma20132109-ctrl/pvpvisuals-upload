# PvPVisuals Render Engine (initial integration)

Added package: `com.pvpvisuals.render.engine`

* `SvintusRenderEngine` owns one `WorldRenderEvents.END_MAIN` callback.
* `RenderFeature` is the extension point.
* `RenderContext` / `RenderQueue` provide line and box commands.
* Geometry is camera-relative, in world units; colors use ARGB.
* The initial backend batches all queued geometry through the existing 1.21.11
  `RenderTypes.debugQuads()` consumer. Depth behavior is therefore the standard
  debug-quad pipeline; explicit x-ray and custom GPU pipelines are a later backend.

Initialize once from `PvpVisualsClient`. Example feature:

```java
SvintusRenderEngine.register((world, queue) ->
    queue.box(-1, 0, -1, 1, 2, 1, 0.025f, 0xFFFF3030)
);
```

Important: coordinates passed to `line` / `box` are relative to the camera,
not absolute world coordinates. A feature that starts with world coordinates
must subtract `world.camera().getPosition()` before enqueueing.

This is an additive first backend. Existing feature renderers are intentionally
not silently redirected: they have different extraction, shaders and depth
requirements. They can be migrated one at a time.
