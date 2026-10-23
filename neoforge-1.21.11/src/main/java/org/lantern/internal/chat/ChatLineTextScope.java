package org.lantern.internal.chat;

/**
 * 1.21.11 的聊天行文字不再带颜色参数，底色由 GuiGraphics 通用文字收集器按白色生成。
 * ChatComponentLineMixin 在画聊天行的那一次调用期间打开本作用域，ChatTextCollectorMixin
 * 只在作用域内替换底色，界面上其它文字不受影响。只在渲染线程使用。
 */
public final class ChatLineTextScope {
    private static int depth;

    private ChatLineTextScope() {
    }

    public static void enter() {
        depth++;
    }

    public static void exit() {
        depth--;
    }

    public static boolean active() {
        return depth > 0;
    }
}
