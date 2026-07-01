package com.example.test5.device.modbus;

/**
 * 滚筒锅寄存器地址，与上位机 StirFryEnumType 枚举值一致。
 */
public final class DrumPotRegisters {

    public static final int SYSTEM_STOP = 0;
    public static final int MOTOR_ENABLE = 1;
    public static final int MOTOR_DIR = 2;
    public static final int WATER_VALVE = 3;
    public static final int AIR_VALVE = 4;
    public static final int WATER_PUMP = 5;
    public static final int ROTATE_SPEED = 19;
    public static final int HEAT_POWER = 20;
    public static final int AXIS_RESET = 23;
    public static final int AXIS_HOME = 26;
    public static final int AXIS_ABSOLUTE_MOVE = 27;
    public static final int AXIS_ABSOLUTE_POSITION = 28;
    public static final int AXIS_HOME_DONE = 39;
    public static final int AXIS_POSITION_DONE = 41;
    public static final int AXIS_CURRENT_POSITION = 53;

    private DrumPotRegisters() {
    }
}
