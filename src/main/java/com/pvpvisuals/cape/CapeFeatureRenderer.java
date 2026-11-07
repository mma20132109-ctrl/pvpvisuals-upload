package com.pvpvisuals.cape;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pvpvisuals.config.PvpConfig;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/**
 * Слой плаща: повторяет ванильный CapeLayer (та же модель, та же анимация — её считает
 * PlayerCapeModel#setupAnim по полям capeFlap/capeLean состояния), но с текстурой мода.
 */
public final class CapeFeatureRenderer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private final PlayerCapeModel model;

	public CapeFeatureRenderer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
		super(parent);
		this.model = new PlayerCapeModel(models.bakeLayer(ModelLayers.PLAYER_CAPE));
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state,
			float yRot, float xRot) {
		if (!PvpConfig.get().cape.enabled) {
			return; // модуль выключен
		}
		if (!CapeModule.visibleFor(state) || state.chestEquipment.is(Items.ELYTRA)) {
			return;
		}
		Identifier texture = CapeModule.texture();
		if (texture == null) {
			return;
		}

		poseStack.pushPose();
		if (!state.chestEquipment.isEmpty()) {
			// как в ванильном слое: сдвиг плаща из-за нагрудника
			poseStack.translate(0.0F, -0.053125F, 0.06875F);
		}
		// ПРОВЕРИТЬ: сигнатуру OrderedSubmitNodeCollector#submitModel (model, state, pose, renderType,
		// light, overlay, tintedColor, sprite, outlineColor, crumblingOverlay) в 1.21.11.
		collector.submitModel(model, state, poseStack, RenderTypes.entityTranslucent(texture), light,
				OverlayTexture.NO_OVERLAY, CapeModule.tint(), null, state.outlineColor, null);
		poseStack.popPose();
	}
}
