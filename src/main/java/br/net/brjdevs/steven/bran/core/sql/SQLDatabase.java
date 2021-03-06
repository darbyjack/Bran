package br.net.brjdevs.steven.bran.core.sql;

import br.net.brjdevs.steven.bran.core.client.Bran;
import com.mysql.cj.jdbc.MysqlDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class SQLDatabase {
    
    private static SQLDatabase sql;

    public static void start(String dbPwd) {
        sql = new SQLDatabase(dbPwd);
    }
    
    private MysqlDataSource dataSource;
    
    private SQLDatabase(String dbPwd) {
        dataSource = new MysqlDataSource();
        dataSource.setDatabaseName("botlogs");
        dataSource.setUser("root");
        dataSource.setPassword(dbPwd);
        dataSource.setServerName("localhost");
        dataSource.setURL(dataSource.getURL() + "?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC&autoReconnect=true&useSSL=false");
    }
    
    public static SQLDatabase getInstance() {
        return sql;
    }
    
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    public SQLAction run(SQLTask task) throws SQLException {
        return new SQLAction(getConnection(), task);
    }
}
