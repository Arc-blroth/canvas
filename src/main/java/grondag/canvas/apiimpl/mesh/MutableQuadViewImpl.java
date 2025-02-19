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

package grondag.canvas.apiimpl.mesh;

import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.BASE_QUAD_STRIDE;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.BASE_VERTEX_STRIDE;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.EMPTY;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.HEADER_BITS;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.HEADER_COLOR_INDEX;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.HEADER_MATERIAL;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.HEADER_SPRITE;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.HEADER_STRIDE;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.HEADER_TAG;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.UV_PRECISE_UNIT_VALUE;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.VERTEX_LIGHTMAP;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.VERTEX_NORMAL;
import static grondag.canvas.apiimpl.mesh.MeshEncodingHelper.VERTEX_X;

import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;

import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.model.ModelHelper;

import grondag.canvas.apiimpl.Canvas;
import grondag.canvas.apiimpl.util.NormalHelper;
import grondag.canvas.apiimpl.util.TextureHelper;
import grondag.canvas.material.state.MaterialFinderImpl;
import grondag.canvas.material.state.RenderMaterialImpl;
import grondag.canvas.mixinterface.Matrix3fExt;
import grondag.canvas.mixinterface.Matrix4fExt;
import grondag.canvas.mixinterface.SpriteExt;
import grondag.frex.api.mesh.FrexVertexConsumer;
import grondag.frex.api.mesh.QuadEmitter;

/**
 * Almost-concrete implementation of a mutable quad. The only missing part is {@link #emit()},
 * because that depends on where/how it is used. (Mesh encoding vs. render-time transformation).
 */
public abstract class MutableQuadViewImpl extends QuadViewImpl implements QuadEmitter, FrexVertexConsumer {
	// PERF: pack into one array for LOR?
	public final float[] u = new float[4];
	public final float[] v = new float[4];
	// vanilla light outputs
	public final float[] ao = new float[4];
	protected RenderMaterialImpl defaultMaterial = Canvas.MATERIAL_STANDARD;

	private int vertexIndex = 0;

	public final void begin(int[] data, int baseIndex) {
		this.data = data;
		this.baseIndex = baseIndex;
		clear();
	}

	@Override
	public MutableQuadViewImpl defaultMaterial(RenderMaterial defaultMaterial) {
		this.defaultMaterial = (RenderMaterialImpl) defaultMaterial;
		return this;
	}

	/**
	 * Call before emit or mesh incorporation.
	 */
	public final void complete() {
		computeGeometry();
		normalizeSpritesIfNeeded();
		vertexIndex = 0;
	}

	public void clear() {
		System.arraycopy(EMPTY, 0, data, baseIndex, MeshEncodingHelper.MAX_QUAD_STRIDE);
		isGeometryInvalid = true;
		packedFaceNormal = -1;
		nominalFaceId = ModelHelper.NULL_FACE_ID;
		normalFlags(0);
		// tag(0); seems redundant - handled by array copy
		colorIndex(-1);
		cullFace(null);
		material(defaultMaterial);
		isSpriteInterpolated = false;
		vertexIndex = 0;
	}

	@Override
	public final MutableQuadViewImpl material(RenderMaterial material) {
		if (material == null) {
			material = defaultMaterial;
		}

		data[baseIndex + HEADER_MATERIAL] = ((RenderMaterialImpl) material).index;

		assert RenderMaterialImpl.fromIndex(data[baseIndex + HEADER_MATERIAL]) == material;

		return this;
	}

	public final MutableQuadViewImpl cullFace(int faceId) {
		data[baseIndex + HEADER_BITS] = MeshEncodingHelper.cullFace(data[baseIndex + HEADER_BITS], faceId);
		nominalFaceId = faceId;
		return this;
	}

	@Override
	@Deprecated
	public final MutableQuadViewImpl cullFace(Direction face) {
		return cullFace(ModelHelper.toFaceIndex(face));
	}

	public final MutableQuadViewImpl nominalFace(int faceId) {
		nominalFaceId = faceId;
		return this;
	}

	@Override
	@Deprecated
	public final MutableQuadViewImpl nominalFace(Direction face) {
		return nominalFace(ModelHelper.toFaceIndex(face));
	}

	@Override
	public final MutableQuadViewImpl colorIndex(int colorIndex) {
		data[baseIndex + HEADER_COLOR_INDEX] = colorIndex;
		return this;
	}

	@Override
	public final MutableQuadViewImpl tag(int tag) {
		data[baseIndex + HEADER_TAG] = tag;
		return this;
	}

	private void convertVanillaUvPrecision() {
		// Convert sprite data from float to fixed precision
		int index = baseIndex + colorOffset(0) + 1;

		for (int i = 0; i < 4; ++i) {
			data[index] = (int) (Float.intBitsToFloat(data[index]) * UV_PRECISE_UNIT_VALUE);
			data[index + 1] = (int) (Float.intBitsToFloat(data[index + 1]) * UV_PRECISE_UNIT_VALUE);
			index += BASE_VERTEX_STRIDE;
		}
	}

	@Deprecated
	@Override
	public final MutableQuadViewImpl fromVanilla(int[] quadData, int startIndex, boolean isItem) {
		System.arraycopy(quadData, startIndex, data, baseIndex + HEADER_STRIDE, BASE_QUAD_STRIDE);
		convertVanillaUvPrecision();
		normalizeSprite();
		isSpriteInterpolated = false;

		isGeometryInvalid = true;
		packedFaceNormal = -1;
		return this;
	}

	@Override
	public final MutableQuadViewImpl fromVanilla(BakedQuad quad, RenderMaterial material, Direction cullFace) {
		return fromVanilla(quad, material, ModelHelper.toFaceIndex(cullFace));
	}

	public final MutableQuadViewImpl fromVanilla(BakedQuad quad, RenderMaterial material, int cullFaceId) {
		System.arraycopy(quad.getVertexData(), 0, data, baseIndex + HEADER_STRIDE, BASE_QUAD_STRIDE);
		material(material);
		convertVanillaUvPrecision();
		normalizeSprite();
		isSpriteInterpolated = false;
		data[baseIndex + HEADER_BITS] = MeshEncodingHelper.cullFace(0, cullFaceId);
		nominalFaceId = ModelHelper.toFaceIndex(quad.getFace());
		data[baseIndex + HEADER_COLOR_INDEX] = quad.getColorIndex();
		data[baseIndex + HEADER_TAG] = 0;
		isGeometryInvalid = true;
		packedFaceNormal = -1;
		return this;
	}

	@Override
	public MutableQuadViewImpl pos(int vertexIndex, float x, float y, float z) {
		final int index = baseIndex + vertexIndex * BASE_VERTEX_STRIDE + VERTEX_X;
		data[index] = Float.floatToRawIntBits(x);
		data[index + 1] = Float.floatToRawIntBits(y);
		data[index + 2] = Float.floatToRawIntBits(z);
		isGeometryInvalid = true;
		packedFaceNormal = -1;
		return this;
	}

	public void normalFlags(int flags) {
		data[baseIndex + HEADER_BITS] = MeshEncodingHelper.normalFlags(data[baseIndex + HEADER_BITS], flags);
	}

	@Override
	public MutableQuadViewImpl normal(int vertexIndex, float x, float y, float z) {
		normalFlags(normalFlags() | (1 << vertexIndex));
		data[baseIndex + vertexIndex * BASE_VERTEX_STRIDE + VERTEX_NORMAL] = NormalHelper.packNormal(x, y, z);
		return this;
	}

	/**
	 * Internal helper method. Copies face normals to vertex normals lacking one.
	 */
	public final void populateMissingNormals() {
		final int normalFlags = this.normalFlags();

		if (normalFlags == 0b1111) {
			return;
		}

		final int packedFaceNormal = NormalHelper.packNormal(faceNormal());

		for (int v = 0; v < 4; v++) {
			if ((normalFlags & (1 << v)) == 0) {
				data[baseIndex + v * BASE_VERTEX_STRIDE + VERTEX_NORMAL] = packedFaceNormal;
			}
		}

		normalFlags(0b1111);
	}

	@Override
	public MutableQuadViewImpl lightmap(int vertexIndex, int lightmap) {
		data[baseIndex + vertexIndex * BASE_VERTEX_STRIDE + VERTEX_LIGHTMAP] = lightmap;
		return this;
	}

	@Override
	public MutableQuadViewImpl vertexColor(int vertexIndex, int color) {
		data[baseIndex + colorOffset(vertexIndex)] = color;
		return this;
	}

	public final void setSpriteNormalized(boolean isNormalized) {
		if (isNormalized) {
			isSpriteInterpolated = false;
		} else if (material().texture.isAtlas()) {
			isSpriteInterpolated = true;
		}
	}

	public MutableQuadViewImpl spriteFloat(int vertexIndex, float u, float v) {
		final int i = baseIndex + colorOffset(vertexIndex) + 1;
		data[i] = (int) (u * UV_PRECISE_UNIT_VALUE + 0.5f);
		data[i + 1] = (int) (v * UV_PRECISE_UNIT_VALUE + 0.5f);
		return this;
	}

	/**
	 * Must call {@link #spriteId(int, int)} separately.
	 */
	public MutableQuadViewImpl spritePrecise(int vertexIndex, int u, int v) {
		final int i = baseIndex + colorOffset(vertexIndex) + 1;
		data[i] = u;
		data[i + 1] = v;
		assert isSpriteNormalized();
		return this;
	}

	public void normalizeSpritesIfNeeded() {
		if (isSpriteInterpolated) {
			normalizeSprite();
			isSpriteInterpolated = false;
		}
	}

	private void normalizeSprite() {
		if (material().texture.isAtlas()) {
			final Sprite sprite = findSprite();
			final int spriteId = ((SpriteExt) sprite).canvas_id();
			final float u0 = sprite.getMinU();
			final float v0 = sprite.getMinV();
			final float uSpanInv = 1f / (sprite.getMaxU() - u0);
			final float vSpanInv = 1f / (sprite.getMaxV() - v0);

			spriteFloat(0, (spriteFloatU(0) - u0) * uSpanInv, (spriteFloatV(0) - v0) * vSpanInv);
			spriteFloat(1, (spriteFloatU(1) - u0) * uSpanInv, (spriteFloatV(1) - v0) * vSpanInv);
			spriteFloat(2, (spriteFloatU(2) - u0) * uSpanInv, (spriteFloatV(2) - v0) * vSpanInv);
			spriteFloat(3, (spriteFloatU(3) - u0) * uSpanInv, (spriteFloatV(3) - v0) * vSpanInv);
			spriteId(spriteId);
		} else {
			assert false : "Attempt to normalize non-atlas sprite coordinates.";
		}
	}

	/**
	 * Same as logic in SpriteFinder but can assume sprites are mapped - avoids checks.
	 */
	private Sprite findSprite() {
		float u = 0;
		float v = 0;

		for (int i = 0; i < 4; i++) {
			u += spriteFloatU(i);
			v += spriteFloatV(i);
		}

		return material().texture.atlasInfo().spriteFinder().find(u * 0.25f, v * 0.25f);
	}

	@Override
	public MutableQuadViewImpl sprite(int vertexIndex, float u, float v) {
		spriteFloat(vertexIndex, u, v);

		// true for whole quad so only need for one vertex
		if (vertexIndex == 0) {
			setSpriteNormalized(false);
		} else {
			assert !isSpriteNormalized() || !material().texture.isAtlas();
		}

		return this;
	}

	@Override
	public MutableQuadViewImpl spriteBake(Sprite sprite, int bakeFlags) {
		TextureHelper.bakeSprite(this, sprite, bakeFlags);
		return this;
	}

	public MutableQuadViewImpl spriteId(int spriteId) {
		data[baseIndex + HEADER_SPRITE] = spriteId;
		return this;
	}

	@Override
	public void next() {
		// TODO: handle Triangles

		// Auto-emit when we finish a quad.
		// NB: emit will call complete which will set vertex index to zero
		if (vertexIndex == 3) {
			emit();
		} else {
			++vertexIndex;
		}
	}

	@Override
	public void fixedColor(int i, int j, int k, int l) {
		// TODO Auto-generated method stub
	}

	@Override
	public void unfixColor() {
		// TODO Auto-generated method stub
	}

	@Override
	public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
		vertex(x, y, z);
		color(MeshEncodingHelper.packColor(red, green, blue, alpha));
		texture(u, v);
		setOverlay(overlay);
		light(light);
		normal(normalX, normalY, normalZ);
		next();
	}

	@Override
	public FrexVertexConsumer vertex(float x, float y, float z) {
		pos(vertexIndex, x, y, z);
		return this;
	}

	@Override
	public FrexVertexConsumer color(int color) {
		vertexColor(vertexIndex, color);
		return this;
	}

	@Override
	public FrexVertexConsumer texture(float u, float v) {
		sprite(vertexIndex, u, v);
		return this;
	}

	@Override
	public FrexVertexConsumer overlay(int u, int v) {
		setOverlay(u, v);
		return this;
	}

	@Override
	public FrexVertexConsumer overlay(int uv) {
		setOverlay(uv);
		return this;
	}

	protected void setOverlay(int uv) {
		setOverlay(uv & '\uffff', uv >> 16 & '\uffff');
	}

	protected void setOverlay (int u, int v) {
		final boolean hurtOverlay = v == 3;
		final boolean flashOverlay = (v == 10 && u > 7);

		if (hurtOverlay || flashOverlay) {
			final MaterialFinderImpl materialFinder = new MaterialFinderImpl();
			materialFinder.copyFrom(material());
			materialFinder.hurtOverlay(hurtOverlay);
			materialFinder.flashOverlay(flashOverlay);
			material(materialFinder.find());
		}
	}

	@Override
	public FrexVertexConsumer light(int block, int sky) {
		this.lightmap(vertexIndex, (block & 0xFF) | ((sky & 0xFF) << 8));
		return this;
	}

	@Override
	public FrexVertexConsumer light(int lightmap) {
		this.lightmap(vertexIndex, lightmap);
		return this;
	}

	@Override
	public FrexVertexConsumer normal(float x, float y, float z) {
		this.normal(vertexIndex, x, y, z);
		return this;
	}

	@Override
	public FrexVertexConsumer color(int red, int green, int blue, int alpha) {
		return color(MeshEncodingHelper.packColor(red, green, blue, alpha));
	}

	@Override
	public FrexVertexConsumer vertex(Matrix4f matrix, float x, float y, float z) {
		final Matrix4fExt mat = (Matrix4fExt) (Object) matrix;

		final float tx = mat.a00() * x + mat.a01() * y + mat.a02() * z + mat.a03();
		final float ty = mat.a10() * x + mat.a11() * y + mat.a12() * z + mat.a13();
		final float tz = mat.a20() * x + mat.a21() * y + mat.a22() * z + mat.a23();

		return this.vertex(tx, ty, tz);
	}

	@Override
	public FrexVertexConsumer normal(Matrix3f matrix, float x, float y, float z) {
		final Matrix3fExt mat = (Matrix3fExt) (Object) matrix;

		final float tx = mat.a00() * x + mat.a01() * y + mat.a02() * z;
		final float ty = mat.a10() * x + mat.a11() * y + mat.a12() * z;
		final float tz = mat.a20() * x + mat.a21() * y + mat.a22() * z;

		return this.normal(tx, ty, tz);
	}
}
