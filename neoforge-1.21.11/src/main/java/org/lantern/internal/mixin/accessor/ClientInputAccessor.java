package org.lantern.internal.mixin.accessor;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 ClientInput.moveVector 的写入口。
 *
 * 输入锁清 keyPresses 是不够的：实际驱动移动的是 moveVector（LocalPlayer.applyInput
 * 用它算 xxa/zza），KeyboardInput.tick 里两者是分别赋值的。只清按键位的话
 * 玩家照样会走，锁等于没上。
 *
 * moveVector 在 ClientInput 里是 protected，插件代码够不着，只能开这个访问器
 */
@Mixin(ClientInput.class)
public interface ClientInputAccessor {

    @Accessor("moveVector")
    void lantern$setMoveVector(Vec2 moveVector);
}
