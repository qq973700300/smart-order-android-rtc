package com.example.test5.order.mq;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** 与 SSKJYingJiang HttpModel 对应的 MQ 订单 JSON。 */
public final class OrderMqModels {

    private OrderMqModels() {
    }

    public static final class DataItem {
        @SerializedName("SubscribeId")
        public int subscribeId;
        @SerializedName("EquipmentNum")
        public String equipmentNum;
        @SerializedName("Topic")
        public String topic;
        @SerializedName("Message")
        public String message;
        @SerializedName("State")
        public int state;
        @SerializedName("CreateTime")
        public String createTime;
        @SerializedName("StoreId")
        public int storeId;
        @SerializedName("OrderNumber")
        public String orderNumber;
        @SerializedName("SortIndex")
        public int sortIndex;
        @SerializedName("DishId")
        public int dishId;
    }

    public static final class MessageDetail {
        @SerializedName("Detail")
        public List<DetailItem> detail;
    }

    public static final class DetailItem {
        @SerializedName("EquipmentID")
        public int equipmentId;
        @SerializedName("EquipmentNum")
        public String equipmentNum;
        @SerializedName("EquipmentName")
        public String equipmentName;
        @SerializedName("DishId")
        public int dishId;
        @SerializedName("DishName")
        public String dishName;
        @SerializedName("DishTaste")
        public String dishTaste;
        @SerializedName("DishDescription")
        public String dishDescription;
        @SerializedName("CookNumber")
        public String cookNumber;
        @SerializedName("TableNumber")
        public String tableNumber;
        @SerializedName("DishCategory")
        public String dishCategory;
        @SerializedName("Weight")
        public double weight;
        @SerializedName("Price")
        public double price;
    }

    public static final class VoiceItem {
        @SerializedName("EquipmentName")
        public String equipmentName;
        @SerializedName("DishId")
        public int dishId;
        @SerializedName("DishName")
        public String dishName;
        @SerializedName("DishDescription")
        public String dishDescription;
        @SerializedName("CookNumber")
        public String cookNumber;
        @SerializedName("TableNumber")
        public String tableNumber;
        @SerializedName("DishCategory")
        public String dishCategory;
    }
}
