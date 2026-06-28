package com.example.test5.order;

/** 菜单展示项（与 test3 MenuCatalog 一致） */
public final class MenuItem {

    public final String name;
    public final int imageResId;
    public final String subtitle;

    public MenuItem(String name, int imageResId, String subtitle) {
        this.name = name;
        this.imageResId = imageResId;
        this.subtitle = subtitle;
    }
}
