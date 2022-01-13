package com.zjh.logviewer.model;

import org.jflame.commons.model.TreeNode;
import org.jflame.commons.util.DateHelper;

import java.beans.Transient;

public class  FileAttri extends TreeNode implements Comparable<FileAttri> {

    private static final long serialVersionUID = 3862330131151025200L;
    private String path;
    private long lastUpdateDate;
    private String state;

    public void setSize(String size) {
        setAttribute("size", size);
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getUpdateDateText() {
        return lastUpdateDate > 0 ? DateHelper.formatLong(new java.util.Date(lastUpdateDate * 1000)) : "";
    }

    @Transient
    public long getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(long lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    public Boolean isDir() {
        return Boolean.TRUE.equals(getAttribute("dir"));
    }

    public void setDir(boolean isDir) {
        setAttribute("dir", isDir);
    }

    @Override
    public int compareTo(FileAttri o) {
        if (this.getLastUpdateDate() > o.getLastUpdateDate())
            return 1;
        else if (this.getLastUpdateDate() < o.getLastUpdateDate())
            return -1;
        else {
            return 0;
        }
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

}
