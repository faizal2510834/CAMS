package com.cams.service;

import com.cams.model.Category;
import com.cams.model.Department;
import com.cams.model.Location;

import java.util.List;
import java.util.Map;

/**
 * Service managing master lookup data (Departments, Locations, Categories).
 */
public interface MasterDataService {

    // Departments
    List<Department> getAllDepartments(String active);
    Department getDepartmentById(String id);
    Department createDepartment(Department department);
    Department updateDepartment(String id, Department department);
    void deactivateDepartment(String id);

    // Locations
    List<Location> getAllLocations(String active);
    Location getLocationById(String id);
    Location createLocation(Location location);
    Location updateLocation(String id, Location location);
    void deactivateLocation(String id);

    // Categories
    List<Category> getAllCategories(String active);
    Category getCategoryById(String id);
    Category createCategory(Category category);
    Category updateCategory(String id, Category category);
    void deactivateCategory(String id);

    // Options for Asset dropdowns
    Map<String, List<String>> getMasterDataOptions();
}
