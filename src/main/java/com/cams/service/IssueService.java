package com.cams.service;

import com.cams.model.AssetIssue;
import com.cams.model.PagedResult;
import com.cams.model.User;

import java.sql.SQLException;

public interface IssueService {

    AssetIssue createIssue(User sessionUser, AssetIssue issue) throws SQLException;

    AssetIssue returnIssue(User sessionUser, String issueId, String conditionOnReturn, String returnRemarks) throws SQLException;

    AssetIssue getIssueById(User sessionUser, String issueId) throws SQLException;

    PagedResult<AssetIssue> searchIssues(User sessionUser, String status, String assetId,
                                         Long userId, String deptId, int page, int size) throws SQLException;
}
