package xuanmo.arcartxsuite.menu.service;

import xuanmo.arcartxsuite.menu.config.MenuButtonDefinition;
import xuanmo.arcartxsuite.menu.config.MenuDefinition;
import xuanmo.arcartxsuite.menu.config.MenuLayoutType;
import xuanmo.arcartxsuite.menu.service.MenuPacketFactory.MenuView;

/**
 * 菜单会话，记录玩家当前打开的菜单状态，包括菜单 ID、布局类型、运行时 UI ID、页索引和点击时间戳。
 * <p>
 * 由 {@link MenuService} 创建和管理，每个玩家同时持有一个会话。
 */
public final class MenuSession {

    private final String menuId;
    private final MenuLayoutType layout;
    private final String runtimeUiId;
    private int pageIndex;
    private long lastClickAt;
    private int actionDepth;
    private int publishedPageIndex = -1;
    private MenuDefinition publishedDefinition;
    private MenuView publishedView;
    private boolean tokenConsumed;

    /**
     * 构造菜单会话。
     *
     * @param menuId      菜单 ID
     * @param layout      布局类型
     * @param runtimeUiId 运行时 UI ID
     * @param pageIndex   起始页索引
     */
    public MenuSession(String menuId, MenuLayoutType layout, String runtimeUiId, int pageIndex) {
        this.menuId = menuId;
        this.layout = layout;
        this.runtimeUiId = runtimeUiId;
        this.pageIndex = pageIndex;
    }

    /**
     * 返回当前菜单 ID。
     *
     * @return 菜单 ID
     */
    public String menuId() {
        return menuId;
    }

    /**
     * 返回布局类型。
     *
     * @return 布局类型
     */
    public MenuLayoutType layout() {
        return layout;
    }

    /**
     * 返回运行时 UI ID。
     *
     * @return 运行时 UI ID
     */
    public String runtimeUiId() {
        return runtimeUiId;
    }

    /**
     * 返回当前页索引。
     *
     * @return 页索引
     */
    public int pageIndex() {
        return pageIndex;
    }

    /**
     * 设置当前页索引，负值会被截断为 0。
     *
     * @param pageIndex 新的页索引
     */
    public void setPageIndex(int pageIndex) {
        this.pageIndex = Math.max(0, pageIndex);
    }

    /**
     * 返回上次点击的时间戳。
     *
     * @return 上次点击时间戳（毫秒）
     */
    public long lastClickAt() {
        return lastClickAt;
    }

    /**
     * 记录本次点击的时间戳，用于点击冷却判断。
     *
     * @param timestamp 点击时间戳（毫秒）
     */
    public void markClick(long timestamp) {
        this.lastClickAt = timestamp;
    }

    void beginActions() {
        actionDepth++;
    }

    void endActions() {
        actionDepth--;
    }

    boolean actionsRunning() {
        return actionDepth > 0;
    }

    boolean isPublished(MenuDefinition definition) {
        return publishedView != null && publishedDefinition == definition && publishedPageIndex == pageIndex;
    }

    MenuButtonDefinition displayedButton(MenuDefinition definition, String buttonId, boolean footer) {
        return isPublished(definition) ? (footer ? publishedView.footers() : publishedView.buttons()).get(buttonId) : null;
    }

    MenuView publishedView() { return publishedView; }

    boolean tokenConsumed() { return tokenConsumed; }

    void consumeToken() { tokenConsumed = true; }

    boolean unchanged(MenuDefinition definition, MenuView view) {
        return !tokenConsumed && isPublished(definition)
            && (publishedView == view || publishedView.payload().equals(view.payload()));
    }

    void invalidatePublication() {
        publishedView = null;
    }

    void published(MenuDefinition definition, int pageIndex, MenuView view) {
        publishedDefinition = definition;
        publishedPageIndex = pageIndex;
        publishedView = view;
        tokenConsumed = false;
    }
}
