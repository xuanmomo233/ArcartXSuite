package xuanmo.arcartxsuite.menu.service;

import xuanmo.arcartxsuite.menu.config.MenuLayoutType;

public final class MenuSession {

    private final String menuId;
    private final MenuLayoutType layout;
    private int pageIndex;
    private long lastClickAt;

    public MenuSession(String menuId, MenuLayoutType layout, int pageIndex) {
        this.menuId = menuId;
        this.layout = layout;
        this.pageIndex = pageIndex;
    }

    public String menuId() {
        return menuId;
    }

    public MenuLayoutType layout() {
        return layout;
    }

    public int pageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = Math.max(0, pageIndex);
    }

    public long lastClickAt() {
        return lastClickAt;
    }

    public void markClick(long timestamp) {
        this.lastClickAt = timestamp;
    }
}
