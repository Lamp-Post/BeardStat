package com.tehbeard.beardstat.dataproviders;

import com.google.gson.stream.JsonReader;
import java.sql.SQLException;

import com.tehbeard.beardstat.DatabaseConfiguration;
import com.tehbeard.beardstat.DbPlatform;
import com.tehbeard.beardstat.containers.documents.DocumentFile;
import com.tehbeard.beardstat.containers.documents.DocumentHistory;
import com.tehbeard.beardstat.containers.documents.DocumentHistory.DocumentHistoryEntry;
import com.tehbeard.beardstat.containers.documents.DocumentRegistry;
import com.tehbeard.beardstat.containers.documents.IStatDocument;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

public class MysqlStatDataProvider extends JDBCStatDataProvider {

    //Document meta scripts
    public static final String SQL_DOC_META_INSERT = "sql/doc/meta/metaInsert";
    public static final String SQL_DOC_META_LOCK = "sql/doc/meta/metaLock";
    public static final String SQL_DOC_META_UPDATE = "sql/doc/meta/metaUpdate";
    public static final String SQL_DOC_META_DELETE = "sql/doc/meta/metaDelete";
    public static final String SQL_DOC_META_SELECT = "sql/doc/meta/metaSelect";
    public static final String SQL_DOC_META_POLL = "sql/doc/meta/metaPoll";
    //Document store scripts
    public static final String SQL_DOC_STORE_INSERT = "sql/doc/store/storeInsert";
    public static final String SQL_DOC_STORE_SELECT = "sql/doc/store/storeSelect";
    public static final String SQL_DOC_STORE_POLL = "sql/doc/store/storePoll";
    public static final String SQL_DOC_STORE_DELETE = "sql/doc/store/storeDelete";
    public static final String SQL_DOC_STORE_PURGE = "sql/doc/store/storePurge";
    //meta
    private PreparedStatement stmtMetaInsert;
    private PreparedStatement stmtMetaUpdate;
    private PreparedStatement stmtMetaDelete;
    private PreparedStatement stmtMetaSelect;
    private PreparedStatement stmtMetaPoll;
    //docs
    private PreparedStatement stmtDocInsert;
    private PreparedStatement stmtDocSelect;
    private PreparedStatement stmtDocPoll;
    private PreparedStatement stmtDocDelete;
    private PreparedStatement stmtDocPurge;

    public MysqlStatDataProvider(DbPlatform platform, DatabaseConfiguration config) throws SQLException {

        super(platform, "sql", "com.mysql.jdbc.Driver", config);
        this.connectionUrl = String.format("jdbc:mysql://%s:%s/%s", config.host, config.port, config.database);
        this.connectionProperties.put("user", config.username);
        this.connectionProperties.put("password", config.password);
        this.connectionProperties.put("autoReconnect", "true");

        initialise();
    }

    @Override
    protected void prepareStatements() {
        super.prepareStatements();
        //Meta
        stmtMetaInsert = getStatementFromScript(SQL_DOC_META_INSERT, Statement.RETURN_GENERATED_KEYS);
        stmtMetaUpdate = getStatementFromScript(SQL_DOC_META_UPDATE);
        stmtMetaDelete = getStatementFromScript(SQL_DOC_META_DELETE);
        stmtMetaSelect = getStatementFromScript(SQL_DOC_META_SELECT);
        stmtMetaPoll = getStatementFromScript(SQL_DOC_META_POLL);
        //Store
        stmtDocInsert = getStatementFromScript(SQL_DOC_STORE_INSERT, Statement.RETURN_GENERATED_KEYS);
        stmtDocSelect = getStatementFromScript(SQL_DOC_STORE_SELECT);
        stmtDocPoll = getStatementFromScript(SQL_DOC_STORE_POLL);
        stmtDocDelete = getStatementFromScript(SQL_DOC_STORE_DELETE);
        stmtDocPurge = getStatementFromScript(SQL_DOC_STORE_PURGE);

    }

    @Override
    public void generateBackup(File file) {
        platform.getLogger().log(Level.INFO, "Creating backup of database at {0}", file.toString());
        try {
            FileOutputStream fw = new FileOutputStream(file);
            GZIPOutputStream gos = new GZIPOutputStream(fw) {
                {
                    def.setLevel(Deflater.BEST_COMPRESSION);
                }
            };
            dumpToBuffer(new BufferedWriter(new OutputStreamWriter(gos)));
        } catch (IOException ex) {
            Logger.getLogger(MysqlStatDataProvider.class.getName()).log(Level.SEVERE, null, ex);
        }


    }

    private ResultSet query(String sql) throws SQLException {
        return conn.prepareStatement(sql).executeQuery();
    }

    private void dumpToBuffer(BufferedWriter buff) {
        try {

            String version = "-- If restoring to this backup, set stats.database.sql_db_version to : " + config.latestVersion;
            StringBuilder sb = new StringBuilder();
            ResultSet rs = query("SHOW FULL TABLES WHERE Table_type != 'VIEW'");
            while (rs.next()) {
                String tbl = rs.getString(1);
                if (!tbl.startsWith(tblPrefix)) {
                    continue;
                }
                sb.append(version).append("\n");
                sb.append("\n");
                sb.append("-- ----------------------------\n")
                .append("-- Table structure for `").append(tbl)
                .append("`\n-- ----------------------------\n");
                sb.append("DROP TABLE IF EXISTS `").append(tbl).append("`;\n");
                ResultSet rs2 = query("SHOW CREATE TABLE `" + tbl + "`");
                rs2.next();
                String crt = rs2.getString(2) + ";";
                sb.append(crt).append("\n");
                sb.append("\n");
                sb.append("-- ----------------------------\n").append("-- Records for `").append(tbl).append("`\n-- ----------------------------\n");

                ResultSet rss = query("SELECT * FROM " + tbl);
                while (rss.next()) {
                    int colCount = rss.getMetaData().getColumnCount();
                    if (colCount > 0) {
                        sb.append("INSERT INTO ").append(tbl).append(" VALUES(");

                        for (int i = 0; i < colCount; i++) {
                            if (i > 0) {
                                sb.append(",");
                            }
                            String s = "";
                            try {
                                s += "'";
                                s += rss.getObject(i + 1).toString();
                                s += "'";
                            } catch (Exception e) {
                                s = "NULL";
                            }
                            sb.append(s);
                        }
                        sb.append(");\n");
                        buff.append(sb.toString());
                        sb = new StringBuilder();
                    }
                }
            }

            ResultSet rs2 = query("SHOW FULL TABLES WHERE Table_type = 'VIEW'");
            while (rs2.next()) {
                String tbl = rs2.getString(1);

                sb.append("\n");
                sb.append("-- ----------------------------\n")
                .append("-- View structure for `").append(tbl)
                .append("`\n-- ----------------------------\n");
                sb.append("DROP VIEW IF EXISTS `").append(tbl).append("`;\n");
                ResultSet rs3 = query("SHOW CREATE VIEW `" + tbl + "`");
                rs3.next();
                String crt = rs3.getString(2) + ";";
                sb.append(crt).append("\n");
            }

            buff.flush();
            buff.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public DocumentFile pullDocument(int entityId, String domain, String key) {
        DocumentFile file = null;

        try {
            //Look for existing document
            ResultSet rs = getDocumentResultSet(entityId, domain, key);

            //if existing document is found, load it.
            if (rs.next()) {
                int docId = getDocumentId(rs);
                String curRev = getCurrentRev(rs);
                rs.close();

                //Get the latest revision
                stmtDocSelect.setInt(1, docId);
                stmtDocSelect.setString(2, curRev);
                rs = stmtDocSelect.executeQuery();
                //If found, grab it.
                if (rs.next()) {
                    //`parentRev`, `added`,`document`
                    String parentRev = rs.getString("parentRev");
                    Timestamp added = rs.getTimestamp("added");
                    Blob document = rs.getBlob("document");
                    JsonReader jsr = new JsonReader(new InputStreamReader(document.getBinaryStream()));


                    platform.getLogger().fine(new String(document.getBytes(1, (int) document.length())));
                    //Load the document
                    IStatDocument fromJson = DocumentRegistry.instance().fromJson(jsr, IStatDocument.class);

                    file = new DocumentFile(curRev, parentRev, domain, key, fromJson, added);

                }
                rs.close();
            } else {
                platform.getLogger().fine("No document entry found.");
                rs.close();
            }


        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ex) {
                Logger.getLogger(MysqlStatDataProvider.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return file;
    }

    private final int MAX_DOC_SIZE = 16 * 1024 * 1024;//16mb limit

    @Override
    public DocumentFile pushDocument(int entityId, DocumentFile document) throws RevisionMismatchException {


        DocumentFile returnDoc = null;

        try {
            //1) Generate JSON
            byte[] doc = DocumentRegistry.instance().toJson(document.getDocument(),DocumentRegistry.getSerializeAs(document.getDocument().getClass())).getBytes();

            if (doc.length > MAX_DOC_SIZE) {
                throw new RuntimeException("Document exceeds max size.");
            }

            //2) Generate new revision tag.
            MessageDigest digest = MessageDigest.getInstance("SHA1");
            String newRevision = byteArrayToHexString(digest.digest(doc));

            //3) lock meta document record, get headrev revision tag
            ResultSet rs = getDocumentResultSet(entityId, document.getDomain(), document.getKey());

            if (rs.next()) {
                int docId = getDocumentId(rs);
                String headRev = getCurrentRev(rs);
                rs.close();

                if (!headRev.equalsIgnoreCase(document.getRevision())) {
                    throw new RevisionMismatchException(pullDocument(entityId, document.getDomain(), document.getKey()));
                }

                stmtMetaUpdate.setString(1, newRevision);
                stmtMetaUpdate.setInt(2, docId);

                int updResult = stmtMetaUpdate.executeUpdate();
                if (updResult != 1) {
                    throw new SQLException("Update to doc meta table affected " + updResult + " rows instead of 1.");
                }

                //`documentId`, `revision`, `parentRev`, `added`, `document`
                stmtDocInsert.setInt(1, docId);
                stmtDocInsert.setString(2, newRevision);
                stmtDocInsert.setString(3, headRev);
                Timestamp tStamp = new Timestamp(System.currentTimeMillis());
                stmtDocInsert.setTimestamp(4, tStamp);

                stmtDocInsert.setBlob(5, new ByteArrayInputStream(doc));


                stmtDocInsert.executeUpdate();
                rs = stmtDocInsert.getGeneratedKeys();
                rs.next();
                rs.close();
                returnDoc = new DocumentFile(newRevision, headRev, document.getDomain(), document.getKey(), document.getDocument(), tStamp);
            } else {
                rs.close();

                //We are inserting a record
                //`entityId`, `domainId`, `key`, `curRevision`
                stmtMetaInsert.setInt(1, entityId);
                stmtMetaInsert.setInt(2, getDomain(document.getDomain()).getDbId());
                stmtMetaInsert.setString(3, document.getKey());
                stmtMetaInsert.setString(4, newRevision);
                stmtMetaInsert.executeUpdate();
                rs = stmtMetaInsert.getGeneratedKeys();
                rs.next();
                int docId = rs.getInt(1);
                rs.close();

                //`documentId`, `revision`, `parentRev`, `added`, `document`
                stmtDocInsert.setInt(1, docId);
                stmtDocInsert.setString(2, newRevision);
                stmtDocInsert.setNull(3, java.sql.Types.CHAR);
                Timestamp tStamp = new Timestamp(System.currentTimeMillis());
                stmtDocInsert.setTimestamp(4, tStamp);

                stmtDocInsert.setBlob(5, new ByteArrayInputStream(doc));
                stmtDocInsert.execute();

                returnDoc = new DocumentFile(newRevision, null, document.getDomain(), document.getKey(), document.getDocument(), tStamp);
            }
            conn.commit();
        } catch (SQLException e) {
            try {
                platform.mysqlError(e, "push doc");
                conn.rollback();
            } catch (SQLException ex) {
                Logger.getLogger(MysqlStatDataProvider.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(MysqlStatDataProvider.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {

                conn.setAutoCommit(true);
            } catch (SQLException ex) {
                Logger.getLogger(MysqlStatDataProvider.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return returnDoc;
    }

    @Override
    public String[] getDocumentKeysInDomain(int entityId, String domain) {
        try {
            stmtMetaPoll.setInt(1,entityId);
            stmtMetaPoll.setInt(2, getDomain(domain).getDbId());
            ResultSet rs = stmtMetaPoll.executeQuery();

            List<String> keys = new ArrayList<String>();
            while(rs.next()){
                keys.add(rs.getString("key"));
            }
            rs.close();
            return keys.toArray(new String[0]);
        } catch (SQLException ex) {
            Logger.getLogger(MysqlStatDataProvider.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    @Override
    public DocumentHistory getDocumentHistory(int entityId, String domain, String key) {
        ResultSet rs;
        try {
            rs = getDocumentResultSet(entityId, domain, key);
       
        if (rs.next()) {
            int docId = getDocumentId(rs);
            String headRev = getCurrentRev(rs);
            rs.close();
            stmtDocPoll.setInt(1, docId);
            rs = stmtDocPoll.executeQuery();
            DocumentHistory history = new DocumentHistory(domain, key, headRev);


            while(rs.next()){
                history.addEntry(
                        rs.getString("revision"), 
                        rs.getString("parentRev"), 
                        rs.getTimestamp("added"),
                        rs.getInt("storeId"));
            }
            rs.close();
            return history;

        }
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


        return null;
    }

    @Override
    public void deleteDocument(int entityId, String domain, String key, String revision) {
        if(revision == null){

        }
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Get the ResultSet for document, or null on not found
     * @param entityId
     * @param domain
     * @param key
     * @return
     * @throws SQLException 
     */
    private ResultSet getDocumentResultSet(int entityId, String domain,String key) throws SQLException{
        conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        conn.setAutoCommit(false);
        int domainId = getDomain(domain).getDbId();//Get domain int it
        stmtMetaSelect.setInt(1, entityId);
        stmtMetaSelect.setInt(2, domainId);
        stmtMetaSelect.setString(3, key);
        platform.getLogger().log(Level.FINE, "eid: {0}, domainId: {1}, key: {2}", new Object[]{entityId, domainId, key});
        return stmtMetaSelect.executeQuery();

    }

    private String getCurrentRev(ResultSet rs) throws SQLException{
        return rs.getString("curRevision");
    }
    private int getDocumentId(ResultSet rs) throws SQLException{
        return rs.getInt("documentId");
    }

}
