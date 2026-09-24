package com.cams.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Standard container for paginated query results.
 * Contains items, pagination cursor metadata, and total count.
 */
public class PagedResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> items;
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;

    public PagedResult() {
        this.items = Collections.emptyList();
    }

    public PagedResult(List<T> items, int page, int size, long totalItems) {
        this.items = items != null ? items : Collections.emptyList();
        this.page = page;
        this.size = size;
        this.totalItems = totalItems;
        this.totalPages = size > 0 ? (int) Math.ceil((double) totalItems / size) : 0;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(long totalItems) {
        this.totalItems = totalItems;
        this.totalPages = this.size > 0 ? (int) Math.ceil((double) totalItems / this.size) : 0;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}
