/*
 *  Copyright 2019, 2020 grondag
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License.  You may obtain a copy
 *  of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package grondag.canvas.terrain.render;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import grondag.canvas.material.state.RenderState;
import grondag.canvas.render.SkyShadowRenderer;
import grondag.canvas.terrain.occlusion.VisibleRegionList;
import grondag.canvas.terrain.region.RenderRegion;
import grondag.canvas.varia.GFX;

public class TerrainLayerRenderer {
	public static final void render(final VisibleRegionList visibleRegions, double x, double y, double z, boolean isTranslucent) {
		final int visibleRegionCount = visibleRegions.size();

		if (visibleRegionCount == 0) {
			return;
		}

		final String profileString = isTranslucent ? "render_translucent" : "render_solid";
		final MinecraftClient mc = MinecraftClient.getInstance();

		mc.getProfiler().push(profileString);

		final int startIndex = isTranslucent ? visibleRegionCount - 1 : 0;
		final int endIndex = isTranslucent ? -1 : visibleRegionCount;
		final int step = isTranslucent ? -1 : 1;

		//if (Configurator.hdLightmaps()) {
		//	LightmapHdTexture.instance().enable();
		//	DitherTexture.instance().enable();
		//}

		//		final DrawHandler h = DrawHandlers.get(EncodingContext.TERRAIN, shaderContext.pass);
		//		final MaterialVertexFormat format = h.format;
		//		h.setup();

		int ox = 0, oy = 0, oz = 0;

		for (int regionIndex = startIndex; regionIndex != endIndex; regionIndex += step) {
			final RenderRegion builtRegion = visibleRegions.get(regionIndex);

			if (builtRegion == null) {
				continue;
			}

			final DrawableChunk drawable = isTranslucent ? builtRegion.translucentDrawable() : builtRegion.solidDrawable();

			if (!drawable.isClosed()) {
				final ObjectArrayList<DrawableDelegate> delegates = drawable.delegates();

				if (delegates != null) {
					final BlockPos modelOrigin = builtRegion.origin;
					ox = modelOrigin.getX();
					oy = modelOrigin.getY();
					oz = modelOrigin.getZ();

					drawable.vboBuffer.bind();

					final int limit = delegates.size();
					final boolean notShadowPass = !SkyShadowRenderer.isActive();

					for (int i = 0; i < limit; ++i) {
						final DrawableDelegate d = delegates.get(i);
						final RenderState mat = d.renderState();

						if (!mat.condition.affectBlocks || mat.condition.compute()) {
							if (notShadowPass || mat.castShadows) {
								mat.enable(ox, oy, oz);
								d.draw();
							}
						}
					}

					GFX.bindVertexArray(0);
				}
			}
		}

		mc.getProfiler().pop();

		RenderState.disable();

		// Important this happens BEFORE anything that could affect vertex state
		GFX.glBindVertexArray(0);

		//if (Configurator.hdLightmaps()) {
		//	LightmapHdTexture.instance().disable();
		//	DitherTexture.instance().disable();
		//}

		GFX.bindBuffer(GFX.GL_ARRAY_BUFFER, 0);
	}
}
