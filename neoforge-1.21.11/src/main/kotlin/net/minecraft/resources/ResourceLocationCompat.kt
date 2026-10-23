package net.minecraft.resources

// 1.21.11 把 ResourceLocation 改名为 Identifier，根目录共享代码仍按旧名书写。
// 编译后 compileKotlin 会删掉本包下生成的门面类，避免与 minecraft 构成拆分包
typealias ResourceLocation = Identifier
