package org.lantern.internal.mixin.accessor;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.contextualbar.ContextualBarRenderer;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 1.21.6+ 经验条、坐骑跳跃条、定位栏共用 contextual 信息栏，当前显示哪一种只存在这个字段里。
 * 键类型 Gui.ContextualInfo 是包私有，只能用通配符。
 */
@Mixin(Gui.class)
public interface GuiContextualBarAccessor {
    @Accessor("contextualInfoBar")
    Pair<?, ContextualBarRenderer> lantern$getContextualInfoBar();
}
