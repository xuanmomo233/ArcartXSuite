package xuanmo.arcartxsuite.mail.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.mail.model.MailLogEntry;
import xuanmo.arcartxsuite.mail.storage.JdbcMailRepository;

class MailLogRetentionTest {
    @TempDir Path directory;

    @Test
    void keepsTheMostRecentHundredByTimestampAndId() throws Exception {
        var source=source();var repo=repository(source);UUID owner=UUID.randomUUID();
        Instant now=Instant.now();
        for(int i=0;i<120;i++)repo.appendLog(new MailLogEntry(0,owner,"test","log-"+i,now.plusMillis(i)));
        try(var connection=source.getConnection();var sql=connection.createStatement();var rows=sql.executeQuery("SELECT COUNT(*),MIN(created_at),MAX(created_at) FROM mail_logs")){
            assertTrue(rows.next());assertEquals(100,rows.getInt(1));assertEquals(now.plusMillis(20).toEpochMilli(),rows.getLong(2));assertEquals(now.plusMillis(119).toEpochMilli(),rows.getLong(3));
        }finally{repo.close();}
    }

    @Test
    void aPruningFailureDoesNotRollBackTheNewAuditEntry() throws Exception {
        var source=source();var repo=repository(source);UUID owner=UUID.randomUUID();
        for(int i=0;i<100;i++)repo.appendLog(new MailLogEntry(0,owner,"test","existing",Instant.ofEpochMilli(i+1)));
        try(var connection=source.getConnection();var sql=connection.createStatement()){
            sql.execute("CREATE TRIGGER reject_prune BEFORE DELETE ON mail_logs BEGIN SELECT RAISE(FAIL,'isolated pruning failure'); END");
        }
        repo.appendLog(new MailLogEntry(0,owner,"test","must survive",Instant.now()));
        try(var connection=source.getConnection();var sql=connection.createStatement();var rows=sql.executeQuery("SELECT COUNT(*) FROM mail_logs WHERE content='must survive'")){
            assertTrue(rows.next());assertEquals(1,rows.getInt(1));
        }finally{repo.close();}
    }

    private DataSource source() throws Exception {
        var source=mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call->DriverManager.getConnection("jdbc:sqlite:"+directory.resolve("logs.sqlite")));
        return source;
    }
    private JdbcMailRepository repository(DataSource source)throws Exception{
        var repo=new JdbcMailRepository(source,StorageDescriptor.sqlite("logs.sqlite"),directory.toFile(),Logger.getAnonymousLogger());repo.initialize();return repo;
    }
}
