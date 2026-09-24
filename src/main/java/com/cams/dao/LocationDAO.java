package com.cams.dao;

import com.cams.model.Location;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for LOCATIONS table operations.
 */
public interface LocationDAO {

    List<Location> findAll(String active) throws SQLException;

    Optional<Location> findById(String locationId) throws SQLException;

    Optional<Location> findById(Connection conn, String locationId) throws SQLException;

    boolean existsById(String locationId) throws SQLException;

    boolean existsById(Connection conn, String locationId) throws SQLException;

    boolean isActive(String locationId) throws SQLException;

    boolean insert(Location location) throws SQLException;

    boolean update(Location location) throws SQLException;

    boolean deactivate(String locationId) throws SQLException;
}
