package xuanmo.arcartxsuite.api.storage;

import java.util.concurrent.CompletionStage;
import javax.sql.DataSource;

/** 一个模块运行实例的数据源作用域。数据库连接应在后台获取。 */
public interface ModuleStorage {
    DataSource dataSource();

    CompletionStage<Void> ready();

    /** 在宿主存储线程初始化表和索引。退役将等待已提交的初始化退出。 */
    CompletionStage<Void> initialize(Initializer initializer);

    @FunctionalInterface
    interface Initializer {
        void run() throws Exception;
    }

    /**
     * 调用前停止业务接收。封闭本实例的新启用入口，已接收的数据库工作可继续借用
     * 本实例连接。工作退出后拒绝借用，等所有已借连接归还，再释放并启用后继实例。
     */
    void retireAfter(CompletionStage<?> databaseStopped);

    CompletionStage<Void> retired();
}
