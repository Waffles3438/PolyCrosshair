@file:Suppress("UnstableAPIUsage")
package org.polyfrost.crosshair.render

import cc.polyfrost.oneconfig.config.core.OneColor
import cc.polyfrost.oneconfig.images.OneImage
import cc.polyfrost.oneconfig.libs.universal.UResolution
import cc.polyfrost.oneconfig.utils.dsl.mc
import net.minecraft.client.gui.Gui
import net.minecraft.client.renderer.EntityRenderer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.monster.IMob
import net.minecraft.entity.passive.EntityAmbientCreature
import net.minecraft.entity.passive.EntityAnimal
import net.minecraft.entity.passive.EntityVillager
import net.minecraft.entity.passive.EntityWaterMob
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.MovingObjectPosition
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.lwjgl.opengl.GL11
import org.polyfrost.crosshair.config.ModConfig
import org.polyfrost.crosshair.mixin.GuiIngameAccessor
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

object CrosshairRenderer {
    private var texture = DynamicTexture(15, 15)
    private var textureLocation = mc.textureManager.getDynamicTextureLocation("polycrosshair", texture)
    private var whiteTexture = DynamicTexture(15, 15)
    private var whiteTextureLocation = mc.textureManager.getDynamicTextureLocation("polycrosshair", whiteTexture)

    fun updateTexture(image: OneImage) {
        val paddedImage = if (ModConfig.canvaSize % 2 == 1 && ModConfig.newCurrentCrosshair.padToEvenSize) {
            addPixel(image.image)
        } else {
            image.image
        }
        texture = DynamicTexture(paddedImage)
        textureLocation = mc.textureManager.getDynamicTextureLocation("polycrosshair", texture)
        whiteTexture = DynamicTexture(paddedImage.width, paddedImage.height)
        for (posY in 0..<paddedImage.height) {
            for (posX in 0..<paddedImage.width) {
                val color = paddedImage.getRGB(posX, posY)
                if (color shr 24 == 0) continue
                whiteTexture.textureData[posX + posY * paddedImage.width] = -1
            }
        }
        whiteTexture.updateDynamicTexture()
        whiteTextureLocation = mc.textureManager.getDynamicTextureLocation("polycrosshair", whiteTexture)
    }

    @SubscribeEvent
    fun cancel(event: RenderGameOverlayEvent.Pre) {
        if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS || !ModConfig.enabled) return
        GlStateManager.enableAlpha()
        event.isCanceled = true
    }

    fun drawCrosshair(entityRenderer: EntityRenderer) {
        if (!ModConfig.enabled) return
        if ((mc.ingameGUI as? GuiIngameAccessor)?.shouldShowCrosshair() == false) return

        entityRenderer.setupOverlayRendering()
        GlStateManager.pushMatrix()
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
        GlStateManager.enableBlend()
        val renderConfig = ModConfig.renderConfig
        if (renderConfig.invertColor) {
            GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR, 1, 0)
        }
        GlStateManager.enableAlpha()

        GL11.glColor4f(1f, 1f, 1f, 1f)

        mc.textureManager.bindTexture(textureLocation)
        val mcScale = UResolution.scaleFactor.toFloat()
        GlStateManager.scale(1 / mcScale, 1 / mcScale, 1f)
        val crosshair = ModConfig.newCurrentCrosshair
        GlStateManager.translate(crosshair.offsetX.toFloat(), crosshair.offsetY.toFloat(), 0f)
        GlStateManager.scale(mcScale, mcScale, 1f)
        GlStateManager.translate((UResolution.scaledWidth / 2).toDouble(), (UResolution.scaledHeight / 2).toDouble(), 0.0)
        GlStateManager.rotate(crosshair.rotation.toFloat(), 0f, 0f, 1f)
        val padded = ModConfig.canvaSize % 2 == 1 && crosshair.padToEvenSize
        val size = ModConfig.canvaSize + if (padded) 1 else 0
        val x = 0 - 7 - (((ModConfig.canvaSize / 2f).roundToInt() - 8) / 2f)
        val y = 0 - 7 - (((ModConfig.canvaSize / 2f).roundToInt() - 8) / 2f)
        val scale = crosshair.scale / 100f
        val scaledSize = size.toFloat() * scale
        drawScaledCustomSizeModalRect(x + size * (1 - scale) / 2, y + size * (1 - scale) / 2, 0f, 0f, size, size, scaledSize, scaledSize, size.toFloat(), size.toFloat())
        val c = getColor()
        if (c.rgb != -1) {
            mc.textureManager.bindTexture(whiteTextureLocation)
            GL11.glColor4f(c.red / 255f, c.green / 255f, c.blue / 255f, renderConfig.dynamicOpacity / 100f)
            drawScaledCustomSizeModalRect(x + size * (1 - scale) / 2, y + size * (1 - scale) / 2, 0f, 0f, size, size, scaledSize, scaledSize, size.toFloat(), size.toFloat())
        }
        if (renderConfig.invertColor) {
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0)
        }
        GL11.glColor4f(1f, 1f, 1f, 1f)
        GlStateManager.disableBlend()
        GlStateManager.popMatrix()
    }

    val WHITE = OneColor(-1)

    private fun getColor(): OneColor {
        with(ModConfig.renderConfig) {
            if (mc.objectMouseOver == null) return WHITE
            if (mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) return WHITE
            val entity = mc.objectMouseOver.entityHit ?: return WHITE
            if (dynamicColor) {
                if (hostile && entity is IMob) return hostileColor
                if (passive && (entity is EntityVillager || entity is EntityAnimal || entity is EntityAmbientCreature || entity is EntityWaterMob)) return passiveColor
                if (player && entity is EntityPlayer) return playerColor
            }
        }
        return WHITE
    }

    private fun addPixel(original: BufferedImage): BufferedImage {
        // Create a new BufferedImage with 1 pixel added to width and height
        val newWidth = original.width + 1
        val newHeight = original.height + 1
        val resizedImage = BufferedImage(newWidth, newHeight, original.type)

        // Get the Graphics2D object to draw on the new BufferedImage
        val g2d = resizedImage.createGraphics()

        // Draw the original image onto the new image
        g2d.drawImage(original, 0, 0, null)

        // Dispose of the Graphics2D object to release resources
        g2d.dispose()
        return resizedImage
    }

    fun drawScaledCustomSizeModalRect(
        x: Float,
        y: Float,
        u: Float,
        v: Float,
        uWidth: Int,
        vHeight: Int,
        width: Float,
        height: Float,
        tileWidth: Float,
        tileHeight: Float
    ) {
        val f = 1.0f / tileWidth
        val g = 1.0f / tileHeight
        val tessellator = Tessellator.getInstance()
        val worldRenderer = tessellator.worldRenderer
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_TEX)
        worldRenderer.pos(x.toDouble(), (y + height).toDouble(), 0.0)
            .tex((u * f).toDouble(), ((v + vHeight.toFloat()) * g).toDouble()).endVertex()
        worldRenderer.pos((x + width).toDouble(), (y + height).toDouble(), 0.0)
            .tex(((u + uWidth.toFloat()) * f).toDouble(), ((v + vHeight.toFloat()) * g).toDouble()).endVertex()
        worldRenderer.pos((x + width).toDouble(), y.toDouble(), 0.0)
            .tex(((u + uWidth.toFloat()) * f).toDouble(), (v * g).toDouble()).endVertex()
        worldRenderer.pos(x.toDouble(), y.toDouble(), 0.0).tex((u * f).toDouble(), (v * g).toDouble()).endVertex()
        tessellator.draw()
    }
}