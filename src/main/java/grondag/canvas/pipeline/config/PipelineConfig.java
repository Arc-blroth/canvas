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

package grondag.canvas.pipeline.config;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.Identifier;

import grondag.canvas.pipeline.config.util.ConfigContext;
import grondag.canvas.pipeline.config.util.NamedDependency;

public class PipelineConfig {
	// WIP: add to config options
	public boolean smoothBrightnessBidirectionaly = false;
	public int brightnessSmoothingFrames = 20;
	public int rainSmoothingFrames = 500;
	public boolean runVanillaClear = true;

	public final ConfigContext context;
	public final ImageConfig[] images;
	public final PipelineParam[] params;
	public final ProgramConfig[] shaders;
	public final FramebufferConfig[] framebuffers;

	public final PassConfig[] onWorldStart;
	public final PassConfig[] afterRenderHand;
	public final PassConfig[] fabulous;

	@Nullable public final FabulousConfig fabulosity;
	@Nullable public final DrawTargetsConfig drawTargets;

	public final NamedDependency<FramebufferConfig> defaultFramebuffer;

	public final Identifier materialVertexShader;
	public final Identifier materialFragmentShader;

	private PipelineConfig() {
		context = new ConfigContext();
		params = new PipelineParam[0];
		shaders = new ProgramConfig[0];
		onWorldStart = new PassConfig[0];
		afterRenderHand = new PassConfig[0];
		fabulous = new PassConfig[0];
		images = new ImageConfig[] { ImageConfig.defaultMain(context), ImageConfig.defaultDepth(context) };
		framebuffers = new FramebufferConfig[] { FramebufferConfig.makeDefault(context) };
		fabulosity = null;
		drawTargets = DrawTargetsConfig.makeDefault(context);
		defaultFramebuffer = context.frameBuffers.dependOn("default");
		materialVertexShader = new Identifier("canvas:shaders/pipeline/standard.vert");
		materialFragmentShader = new Identifier("canvas:shaders/pipeline/standard.frag");
	}

	PipelineConfig (PipelineConfigBuilder builder) {
		context = builder.context;

		defaultFramebuffer = builder.defaultFramebuffer;
		fabulosity = builder.fabulosity;
		drawTargets = builder.drawTargets;

		params = builder.params.toArray(new PipelineParam[builder.params.size()]);
		fabulous = builder.fabulous.toArray(new PassConfig[builder.fabulous.size()]);
		images = builder.images.toArray(new ImageConfig[builder.images.size()]);
		shaders = builder.shaders.toArray(new ProgramConfig[builder.shaders.size()]);
		framebuffers = builder.framebuffers.toArray(new FramebufferConfig[builder.framebuffers.size()]);
		onWorldStart = builder.onWorldStart.toArray(new PassConfig[builder.onWorldStart.size()]);
		afterRenderHand = builder.afterRenderHand.toArray(new PassConfig[builder.afterRenderHand.size()]);
		materialVertexShader = new Identifier(builder.materialVertexShader);
		materialFragmentShader = new Identifier(builder.materialFragmentShader);
	}

	public static PipelineConfig minimalConfig() {
		return new PipelineConfig();
	}

	public static final Identifier DEFAULT_ID = new Identifier("canvas:pipelines/canvas_standard.json");
}
