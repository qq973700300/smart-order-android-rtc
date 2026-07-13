package com.example.test5.recipe.flow;

/** 节点四向连接点。 */
public final class FlowPort {

    public static final String LEFT = "left";
    public static final String RIGHT = "right";
    public static final String TOP = "top";
    public static final String BOTTOM = "bottom";

    private static final String[] ALL = {LEFT, RIGHT, TOP, BOTTOM};

    private FlowPort() {
    }

    public static String[] all() {
        return ALL;
    }

    public static boolean isValid(String port) {
        if (port == null) {
            return false;
        }
        for (String p : ALL) {
            if (p.equals(port)) {
                return true;
            }
        }
        return false;
    }
}
