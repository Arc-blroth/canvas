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

package grondag.canvas.buffer.encoding;

import java.util.Map;
import java.util.SortedMap;

import com.google.common.base.Predicates;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.util.Util;

import grondag.canvas.material.property.MaterialTarget;
import grondag.canvas.material.state.RenderContextState;
import grondag.canvas.material.state.RenderLayerHelper;
import grondag.canvas.material.state.RenderMaterialImpl;
import grondag.canvas.mixinterface.MultiPhaseExt;
import grondag.frex.api.material.FrexVertexConsumerProvider;
import grondag.frex.api.material.RenderMaterial;

public class CanvasImmediate extends Immediate implements FrexVertexConsumerProvider {
	public final VertexCollectorList collectors = new VertexCollectorList();
	public final RenderContextState contextState;

	public CanvasImmediate(BufferBuilder fallbackBuffer, Map<RenderLayer, BufferBuilder> layerBuffers, RenderContextState contextState) {
		super(fallbackBuffer, layerBuffers);
		this.contextState = contextState;
	}

	@Override
	public VertexConsumer getBuffer(RenderLayer renderLayer) {
		RenderMaterialImpl mat = ((MultiPhaseExt) renderLayer).canvas_materialState();

		if (mat == RenderMaterialImpl.MISSING) {
			return super.getBuffer(renderLayer);
		}

		mat = contextState.mapMaterial(mat);

		if (mat == RenderMaterialImpl.MISSING) {
			return super.getBuffer(renderLayer);
		} else {
			return collectors.consumer.prepare(mat);
		}
	}

	@Override
	public VertexConsumer getConsumer(RenderMaterial material) {
		final RenderMaterialImpl mat = contextState.mapMaterial((RenderMaterialImpl) material);
		return collectors.consumer.prepare(mat);
	}

	public DrawableBuffer prepareDrawable(MaterialTarget target) {
		final ObjectArrayList<ArrayVertexCollector> drawList = collectors.sortedDrawList(target);

		return drawList.isEmpty() ? DrawableBuffer.EMPTY : new DrawableBuffer(drawList);
	}

	public void drawCollectors(MaterialTarget target) {
		final ObjectArrayList<ArrayVertexCollector> drawList = collectors.sortedDrawList(target);

		if (!drawList.isEmpty()) {
			ArrayVertexCollector.draw(drawList);
		}
	}

	@Override
	public void draw() {
		final ObjectArrayList<ArrayVertexCollector> drawList = collectors.sortedDrawList(Predicates.alwaysTrue());

		if (!drawList.isEmpty()) {
			ArrayVertexCollector.draw(drawList);
		}

		super.draw();
	}

	@Override
	public void draw(RenderLayer layer) {
		if (RenderLayerHelper.isExcluded(layer)) {
			super.draw(layer);
		} else {
			final ArrayVertexCollector collector = collectors.getIfExists(((MultiPhaseExt) layer).canvas_materialState());

			if (collector != null && !collector.isEmpty()) {
				collector.draw(true);
			}
		}
	}

	public static SortedMap<RenderLayer, BufferBuilder> entityBuilders() {
		return Util.make(new Object2ObjectLinkedOpenHashMap<>(), (object2ObjectLinkedOpenHashMap) -> {
			assignBufferBuilder(object2ObjectLinkedOpenHashMap, RenderLayer.getTranslucentNoCrumbling());
			assignBufferBuilder(object2ObjectLinkedOpenHashMap, RenderLayer.getArmorGlint());
			assignBufferBuilder(object2ObjectLinkedOpenHashMap, RenderLayer.getArmorEntityGlint());
			assignBufferBuilder(object2ObjectLinkedOpenHashMap, RenderLayer.getGlint());
			assignBufferBuilder(object2ObjectLinkedOpenHashMap, RenderLayer.getDirectGlint());
			assignBufferBuilder(object2ObjectLinkedOpenHashMap, RenderLayer.method_30676());
			assignBufferBuilder(object2ObjectLinkedOpenHashMap, RenderLayer.getEntityGlint());
			assignBufferBuilder(object2ObjectLinkedOpenHashMap, RenderLayer.getDirectEntityGlint());
			assignBufferBuilder(object2ObjectLinkedOpenHashMap, RenderLayer.getWaterMask());
			ModelLoader.BLOCK_DESTRUCTION_RENDER_LAYERS.forEach((renderLayer) -> {
				assignBufferBuilder(object2ObjectLinkedOpenHashMap, renderLayer);
			});
		});
	}

	private static void assignBufferBuilder(Object2ObjectLinkedOpenHashMap<RenderLayer, BufferBuilder> builderStorage, RenderLayer layer) {
		builderStorage.put(layer, new BufferBuilder(layer.getExpectedBufferSize()));
	}
}
