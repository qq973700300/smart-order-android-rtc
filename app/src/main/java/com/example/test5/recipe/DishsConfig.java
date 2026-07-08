package com.example.test5.recipe;

/** 与上位机 DishsConfig.xml 单条记录一致。 */
public final class DishsConfig {

    public String id;
    public String dishName;
    public String dishLocation;
    public boolean statusType;
    public int outWater;
    public int outOil;
    public int outSalt;
    public int outShengSauce;
    public int outLaoSauce;
    public int outVinegar;
    public int friedTime;
    public int whiteSugar;
    public int oldsoySauce;

    public DishsConfig copy() {
        DishsConfig c = new DishsConfig();
        c.id = id;
        c.dishName = dishName;
        c.dishLocation = dishLocation;
        c.statusType = statusType;
        c.outWater = outWater;
        c.outOil = outOil;
        c.outSalt = outSalt;
        c.outShengSauce = outShengSauce;
        c.outLaoSauce = outLaoSauce;
        c.outVinegar = outVinegar;
        c.friedTime = friedTime;
        c.whiteSugar = whiteSugar;
        c.oldsoySauce = oldsoySauce;
        return c;
    }
}
