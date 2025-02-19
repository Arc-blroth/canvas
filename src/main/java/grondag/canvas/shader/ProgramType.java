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

package grondag.canvas.shader;

import net.minecraft.util.Identifier;

import grondag.canvas.shader.data.ShaderStrings;

public enum ProgramType {
	MATERIAL_COLOR(false, ShaderStrings.MATERIAL_MAIN_VERTEX, ShaderStrings.MATERIAL_MAIN_FRAGMENT),
	MATERIAL_DEPTH(true, ShaderStrings.DEPTH_MAIN_VERTEX, ShaderStrings.DEPTH_MAIN_FRAGMENT),
	PROCESS(false, null, null);

	public final String name;
	public final boolean isDepth;
	public final Identifier vertexSource;
	public final Identifier fragmentSource;

	ProgramType(boolean isShadow, Identifier vertexSource, Identifier fragmentSource) {
		name = name().toLowerCase();
		isDepth = isShadow;
		this.vertexSource = vertexSource;
		this.fragmentSource = fragmentSource;
	}
}
