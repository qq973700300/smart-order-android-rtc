package com.example.test5.device.opcua;

/** 根据 BrowseName + DataType 推断调试页按钮能力。 */
final class DrumPotOpcVariableRules {

    private DrumPotOpcVariableRules() {
    }

    static DrumPotOpcKnownNodes.Entry toEntry(String label, String nodeId, String dataType) {
        boolean isBoolean = isBooleanType(dataType);
        DrumPotOpcKnownNodes.Kind kind = isBoolean
                ? DrumPotOpcKnownNodes.Kind.BOOLEAN
                : DrumPotOpcKnownNodes.Kind.NUMBER;
        boolean readOnly = isReadOnlyStatus(label, dataType, isBoolean);
        boolean pulseable = isBoolean && !readOnly && !isWritableBoolean(label);
        boolean writable = !readOnly && (!isBoolean || isWritableBoolean(label));
        return new DrumPotOpcKnownNodes.Entry(label, nodeId, kind, pulseable, writable);
    }

    private static boolean isBooleanType(String dataType) {
        return dataType != null && "Boolean".equalsIgnoreCase(dataType.trim());
    }

    private static boolean isReadOnlyStatus(String label, String dataType, boolean isBoolean) {
        if (label == null) {
            return false;
        }
        if (isBoolean) {
            return label.contains("运行中")
                    || (label.endsWith("中")
                    && (label.contains("洗锅") || label.contains("回原") || label.contains("定位")));
        }
        return label.contains("当前位置");
    }

    /** 少数 Boolean 可写 0/1，其余 Boolean 走脉冲。 */
    private static boolean isWritableBoolean(String label) {
        return "电机正反转".equals(label);
    }
}
