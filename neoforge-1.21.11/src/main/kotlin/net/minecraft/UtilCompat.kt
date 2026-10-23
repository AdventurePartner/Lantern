package net.minecraft

// 1.21.11 把 net.minecraft.Util 挪到了 net.minecraft.util，根目录共享代码仍按旧包引用。
// 编译后 compileKotlin 会删掉本包下生成的门面类，避免与 minecraft 构成拆分包
typealias Util = net.minecraft.util.Util
