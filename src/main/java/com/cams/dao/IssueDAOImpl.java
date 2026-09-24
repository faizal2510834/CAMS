package com.cams.dao;

import com.cams.model.AssetIssue;
import com.cams.model.PagedResult;
import com.cams.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class IssueDAOImpl implements IssueDAO {

    private static final String SELECT_JOINED =
            "SELECT i.issue_id, i.asset_id, i.issued_to_user_id, i.issued_to_department, " +
            "       i.issue_date, i.expected_return_date, i.actual_return_date, i.status, " +
            "       i.condition_on_issue, i.condition_on_return, i.remarks, i.return_remarks, " +
            "       i.issued_by, i.returned_to, i.created_at, i.updated_at, " +
            "       a.asset_name, u.name AS user_name, d.department_name " +
            "FROM asset_issues i " +
            "LEFT JOIN assets a ON i.asset_id = a.asset_id " +
            "LEFT JOIN users u ON i.issued_to_user_id = u.user_id " +
            "LEFT JOIN departments d ON i.issued_to_department = d.department_id ";

    @Override
    public boolean insert(Connection conn, AssetIssue issue) throws SQLException {
        String sql = "INSERT INTO asset_issues (" +
                "issue_id, asset_id, issued_to_user_id, issued_to_department, " +
                "issue_date, expected_return_date, status, condition_on_issue, " +
                "remarks, issued_by, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, issue.getIssueId());
            ps.setString(2, issue.getAssetId());
            if (issue.getIssuedToUserId() != null) {
                ps.setLong(3, issue.getIssuedToUserId());
            } else {
                ps.setNull(3, Types.NUMERIC);
            }
            ps.setString(4, issue.getIssuedToDepartment());
            ps.setDate(5, issue.getIssueDate());
            if (issue.getExpectedReturnDate() != null) {
                ps.setDate(6, issue.getExpectedReturnDate());
            } else {
                ps.setNull(6, Types.DATE);
            }
            ps.setString(7, issue.getStatus() != null ? issue.getStatus() : "ISSUED");
            ps.setString(8, issue.getConditionOnIssue() != null ? issue.getConditionOnIssue() : "GOOD");
            ps.setString(9, issue.getRemarks());
            ps.setString(10, issue.getIssuedBy());

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public Optional<AssetIssue> findById(String issueId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, issueId);
        }
    }

    @Override
    public Optional<AssetIssue> findById(Connection conn, String issueId) throws SQLException {
        String sql = SELECT_JOINED + " WHERE i.issue_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, issueId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public int markReturned(Connection conn, String issueId, Date actualReturnDate,
                            String conditionOnReturn, String returnRemarks, String returnedTo) throws SQLException {
        String sql = "UPDATE asset_issues " +
                "SET actual_return_date = ?, " +
                "    condition_on_return = ?, " +
                "    return_remarks = ?, " +
                "    returned_to = ?, " +
                "    status = 'RETURNED', " +
                "    updated_at = CURRENT_TIMESTAMP " +
                "WHERE issue_id = ? AND actual_return_date IS NULL AND status = 'ISSUED'";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, actualReturnDate);
            ps.setString(2, conditionOnReturn);
            ps.setString(3, returnRemarks);
            ps.setString(4, returnedTo);
            ps.setString(5, issueId);

            return ps.executeUpdate();
        }
    }

    @Override
    public PagedResult<AssetIssue> search(String status, String assetId, Long userId, String deptId, int page, int size) throws SQLException {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status.trim())) {
            where.append("AND i.status = ? ");
            params.add(status.trim().toUpperCase());
        }
        if (assetId != null && !assetId.trim().isEmpty()) {
            where.append("AND UPPER(i.asset_id) LIKE ? ");
            params.add("%" + assetId.trim().toUpperCase() + "%");
        }
        if (userId != null && userId > 0) {
            where.append("AND i.issued_to_user_id = ? ");
            params.add(userId);
        }
        if (deptId != null && !deptId.trim().isEmpty() && !"ALL".equalsIgnoreCase(deptId.trim())) {
            where.append("AND i.issued_to_department = ? ");
            params.add(deptId.trim());
        }

        String countSql = "SELECT COUNT(*) FROM asset_issues i " + where;
        int totalItems = 0;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement countPs = conn.prepareStatement(countSql)) {
            for (int j = 0; j < params.size(); j++) {
                countPs.setObject(j + 1, params.get(j));
            }
            try (ResultSet rs = countPs.executeQuery()) {
                if (rs.next()) {
                    totalItems = rs.getInt(1);
                }
            }
        }

        int offset = (page - 1) * size;
        String querySql = SELECT_JOINED + where +
                " ORDER BY i.created_at DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        List<AssetIssue> items = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(querySql)) {
            int paramIndex = 1;
            for (Object param : params) {
                ps.setObject(paramIndex++, param);
            }
            ps.setInt(paramIndex++, offset);
            ps.setInt(paramIndex, size);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(mapRow(rs));
                }
            }
        }

        return new PagedResult<>(items, page, size, totalItems);
    }

    private AssetIssue mapRow(ResultSet rs) throws SQLException {
        AssetIssue issue = new AssetIssue();
        issue.setIssueId(rs.getString("issue_id"));
        issue.setAssetId(rs.getString("asset_id"));
        long uid = rs.getLong("issued_to_user_id");
        issue.setIssuedToUserId(rs.wasNull() ? null : uid);
        issue.setIssuedToDepartment(rs.getString("issued_to_department"));
        issue.setIssueDate(rs.getDate("issue_date"));
        issue.setExpectedReturnDate(rs.getDate("expected_return_date"));
        issue.setActualReturnDate(rs.getDate("actual_return_date"));
        issue.setStatus(rs.getString("status"));
        issue.setConditionOnIssue(rs.getString("condition_on_issue"));
        issue.setConditionOnReturn(rs.getString("condition_on_return"));
        issue.setRemarks(rs.getString("remarks"));
        issue.setReturnRemarks(rs.getString("return_remarks"));
        issue.setIssuedBy(rs.getString("issued_by"));
        issue.setReturnedTo(rs.getString("returned_to"));
        issue.setCreatedAt(rs.getTimestamp("created_at"));
        issue.setUpdatedAt(rs.getTimestamp("updated_at"));

        issue.setAssetName(rs.getString("asset_name"));
        issue.setIssuedToUserName(rs.getString("user_name"));
        issue.setDepartmentName(rs.getString("department_name"));

        return issue;
    }
}
