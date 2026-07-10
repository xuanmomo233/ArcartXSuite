package xuanmo.arcartxsuite.menu.service;

import xuanmo.arcartxsuite.menu.config.MenuLayoutType;

public final class MenuSession {

    private final String menuId;
    private final MenuLayoutType layout;
    private final String runtimeUiId;
    private int pageIndex;
    private long lastClickAt;

    public MenuSession(String menuId, MenuLayoutType layout, String runtimeUiId, int pageIndex) {
        this.menuId = menuId;
        this.layout = layout;
        this.runtimeUiId = runtimeUiId;
        this.pageIndex = pageIndex;
    }

    public String menuId() {
        return menuId;
    }

    public MenuLayoutType layout() {
        return layout;
    }

    public String runtimeUiId() {
        return runtimeUiId;
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
