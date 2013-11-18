/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tehbeard.beardstat.containers.documents;

import java.sql.Timestamp;

/**
 * Holds metadata for a document
 *
 * @author James
 */
public class DocumentFile<T extends IStatDocument> {

    private boolean archive = false;
    private final String revision;
    private final String parentRevision;
    private final String domain;
    private final String key;
    private final T document;
    private final Timestamp dateCreated;

    public DocumentFile(String revision, String parentRevision, String domain, String key, T document, Timestamp dateCreated) {
        this.revision = revision;
        this.parentRevision = parentRevision;
        this.domain = domain;
        this.key = key;
        this.document = document;
        this.dateCreated = dateCreated;

    }

    public String getRevision() {
        return revision;
    }

    public String getDomain() {
        return domain;
    }

    public String getKey() {
        return key;
    }

    @SuppressWarnings("unchecked")
    public <T extends IStatDocument> T getDocument(Class<T> cl) {
        if (cl.isInstance(document)) {
            return (T) document;
        }
        return null;
    }

    public Timestamp getDateCreated() {
        return dateCreated;
    }

    public boolean shouldArchive() {
        return archive;
    }

    public void setArchiveFlag() {
        archive = true;
    }

    public void clearArchiveFlag() {
        archive = false;
    }

    public String getParentRevision() {
        return parentRevision;
    }
}