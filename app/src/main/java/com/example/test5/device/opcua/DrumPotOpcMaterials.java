package com.example.test5.device.opcua;

import androidx.annotation.Nullable;

/** 滚筒锅 OPC UA 调料/加热 BrowseName（与 PLC 服务器接口一致）。 */
public final class DrumPotOpcMaterials {

    public static final class Def {
        public final String actionId;
        public final String label;
        public final String timeBrowseName;
        public final String startBrowseName;
        @Nullable
        public final String recipeField;

        public Def(String actionId, String label, String timeBrowseName, String startBrowseName, String recipeField) {
            this.actionId = actionId;
            this.label = label;
            this.timeBrowseName = timeBrowseName;
            this.startBrowseName = startBrowseName;
            this.recipeField = recipeField;
        }
    }

    public static final Def WATER = new Def(
            "discharge_water", "加水", "水出料时间1", "水出料启动", "outWater");
    public static final Def OIL = new Def(
            "discharge_oil", "加油", "油出料时间2", "油出料启动", "outOil");
    public static final Def VINEGAR = new Def(
            "discharge_vinegar", "加醋", "醋出料时间3", "醋出料启动", "outVinegar");
    public static final Def SHENG_SAUCE = new Def(
            "discharge_sheng", "加生抽", "生抽出料时间4", "生抽出料启动", "outShengSauce");
    public static final Def OLD_SOY = new Def(
            "discharge_old_soy", "加老抽", "老抽出料时间5", "老抽出料启动", "oldsoySauce");
    public static final Def SALT = new Def(
            "discharge_salt", "加盐", "盐出料时间6", "盐出料启动", "outSalt");
    public static final Def LAO_SAUCE = new Def(
            "discharge_lao", "加鸡精", "味精出料时间7", "味精出料启动", "outLaoSauce");
    public static final Def SUGAR = new Def(
            "discharge_sugar", "加白糖", "糖出料时间8", "糖出料启动", "whiteSugar");

    private static final Def[] ALL = {
            WATER, OIL, VINEGAR, SHENG_SAUCE, OLD_SOY, SALT, LAO_SAUCE, SUGAR
    };

    private DrumPotOpcMaterials() {
    }

    public static Def[] all() {
        return ALL;
    }

    @Nullable
    public static Def findByActionId(String actionId) {
        if (actionId == null) {
            return null;
        }
        for (Def def : ALL) {
            if (def.actionId.equals(actionId)) {
                return def;
            }
        }
        return null;
    }

    public static String defaultAmount(Def def) {
        if (def == OIL) {
            return "5000";
        }
        if (def == SALT || def == SHENG_SAUCE || def == LAO_SAUCE || def == VINEGAR) {
            return "1000";
        }
        return "0";
    }
}
