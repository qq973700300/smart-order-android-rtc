package com.example.test5.order;

import com.example.test5.R;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 菜单标准菜名（与 Restaurant.json Tools enum 一致） */
public final class MenuCatalog {

    private static final List<MenuItem> ITEMS = Collections.unmodifiableList(Arrays.asList(
            new MenuItem("芹菜炒肉", R.drawable.dish_celery_pork, "清爽芹菜配嫩炒猪肉"),
            new MenuItem("酸辣土豆丝", R.drawable.dish_potato, "经典家常酸辣口"),
            new MenuItem("辣椒炒肉", R.drawable.dish_pepper_pork, "湘味下饭首选"),
            new MenuItem("小炒猪肝", R.drawable.dish_liver, "滑嫩鲜香热炒"),
            new MenuItem("小炒黄牛肉", R.drawable.dish_beef, "大火快炒，湘味招牌"),
            new MenuItem("杭椒牛柳", R.drawable.dish_beef_hangjiao, "杭椒清香配嫩牛柳"),
            new MenuItem("黑椒牛柳", R.drawable.dish_beef_blackpepper, "黑椒浓香西式炒法"),
            new MenuItem("水煮牛肉", R.drawable.dish_beef_boiled, "麻辣鲜香、重口下饭"),
            new MenuItem("干锅牛肉", R.drawable.dish_beef_drypot, "干香微辣、锅气十足"),
            new MenuItem("外婆菜炒鸡蛋", R.drawable.dish_egg, "咸香开胃组合")
    ));

    private static final Set<String> NAME_SET;

    static {
        HashSet<String> names = new HashSet<>();
        for (MenuItem item : ITEMS) {
            names.add(item.name);
        }
        NAME_SET = Collections.unmodifiableSet(names);
    }

    private MenuCatalog() {
    }

    public static List<MenuItem> getItems() {
        return ITEMS;
    }

    public static String[] getNames() {
        String[] names = new String[ITEMS.size()];
        for (int i = 0; i < ITEMS.size(); i++) {
            names[i] = ITEMS.get(i).name;
        }
        return names;
    }

    public static boolean isValidDish(String name) {
        return name != null && NAME_SET.contains(name);
    }
}
