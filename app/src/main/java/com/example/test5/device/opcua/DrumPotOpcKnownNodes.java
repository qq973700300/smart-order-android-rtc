package com.example.test5.device.opcua;

/** 调试页 PLC 变量条目（BrowseName / NodeId 均来自 browse 结果）。 */
public final class DrumPotOpcKnownNodes {

    public enum Kind {
        BOOLEAN,
        NUMBER
    }

    public static final class Entry {
        public final String label;
        public final String nodeId;
        public final Kind kind;
        /** Boolean 触发类：可读 + 脉冲置 true */
        public final boolean pulseable;
        /** 是否允许写入（只读状态位为 false） */
        public final boolean writable;

        public Entry(String label, String nodeId, Kind kind, boolean pulseable, boolean writable) {
            this.label = label;
            this.nodeId = nodeId;
            this.kind = kind;
            this.pulseable = pulseable;
            this.writable = writable;
        }
    }

    private DrumPotOpcKnownNodes() {
    }
}
