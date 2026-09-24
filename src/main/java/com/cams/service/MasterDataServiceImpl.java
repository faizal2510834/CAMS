package com.cams.service;

import com.cams.dao.*;
import com.cams.model.Category;
import com.cams.model.Department;
import com.cams.model.Location;
import com.cams.util.AssetConstants;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class MasterDataServiceImpl implements MasterDataService {

    private final DepartmentDAO departmentDAO;
    private final LocationDAO locationDAO;
    private final CategoryDAO categoryDAO;

    public MasterDataServiceImpl() {
        this(new DepartmentDAOImpl(), new LocationDAOImpl(), new CategoryDAOImpl());
    }

    public MasterDataServiceImpl(DepartmentDAO departmentDAO, LocationDAO locationDAO, CategoryDAO categoryDAO) {
        this.departmentDAO = departmentDAO;
        this.locationDAO = locationDAO;
        this.categoryDAO = categoryDAO;
    }

    // ==========================================
    // DEPARTMENTS
    // ==========================================
    @Override
    public List<Department> getAllDepartments(String active) {
        try {
            return departmentDAO.findAll(active);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch departments: " + e.getMessage(), e);
        }
    }

    @Override
    public Department getDepartmentById(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new MasterDataValidationException("Department ID is required");
        }
        try {
            return departmentDAO.findById(id.trim())
                    .orElseThrow(() -> new MasterDataNotFoundException("Department not found: " + id));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch department: " + e.getMessage(), e);
        }
    }

    @Override
    public Department createDepartment(Department department) {
        validateDepartment(department, true);
        try {
            if (departmentDAO.existsById(department.getDepartmentId().trim())) {
                throw new MasterDataConflictException("Department ID '" + department.getDepartmentId().trim() + "' already exists");
            }
            departmentDAO.insert(department);
            return departmentDAO.findById(department.getDepartmentId().trim()).orElse(department);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create department: " + e.getMessage(), e);
        }
    }

    @Override
    public Department updateDepartment(String id, Department department) {
        if (id == null || id.trim().isEmpty()) {
            throw new MasterDataValidationException("Department ID is required");
        }
        validateDepartment(department, false);
        department.setDepartmentId(id.trim());
        try {
            if (!departmentDAO.existsById(id.trim())) {
                throw new MasterDataNotFoundException("Department not found: " + id);
            }
            departmentDAO.update(department);
            return departmentDAO.findById(id.trim()).orElse(department);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update department: " + e.getMessage(), e);
        }
    }

    @Override
    public void deactivateDepartment(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new MasterDataValidationException("Department ID is required");
        }
        try {
            if (!departmentDAO.existsById(id.trim())) {
                throw new MasterDataNotFoundException("Department not found: " + id);
            }
            departmentDAO.deactivate(id.trim());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to deactivate department: " + e.getMessage(), e);
        }
    }

    private void validateDepartment(Department department, boolean checkId) {
        if (department == null) {
            throw new MasterDataValidationException("Department payload cannot be empty");
        }
        if (checkId) {
            if (department.getDepartmentId() == null || department.getDepartmentId().trim().isEmpty()) {
                throw new MasterDataValidationException("Department ID is required");
            }
        }
        if (department.getDepartmentName() == null || department.getDepartmentName().trim().isEmpty()) {
            throw new MasterDataValidationException("Department Name is required");
        }
    }

    // ==========================================
    // LOCATIONS
    // ==========================================
    @Override
    public List<Location> getAllLocations(String active) {
        try {
            return locationDAO.findAll(active);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch locations: " + e.getMessage(), e);
        }
    }

    @Override
    public Location getLocationById(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new MasterDataValidationException("Location ID is required");
        }
        try {
            return locationDAO.findById(id.trim())
                    .orElseThrow(() -> new MasterDataNotFoundException("Location not found: " + id));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch location: " + e.getMessage(), e);
        }
    }

    @Override
    public Location createLocation(Location location) {
        validateLocation(location, true);
        try {
            if (locationDAO.existsById(location.getLocationId().trim())) {
                throw new MasterDataConflictException("Location ID '" + location.getLocationId().trim() + "' already exists");
            }
            locationDAO.insert(location);
            return locationDAO.findById(location.getLocationId().trim()).orElse(location);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create location: " + e.getMessage(), e);
        }
    }

    @Override
    public Location updateLocation(String id, Location location) {
        if (id == null || id.trim().isEmpty()) {
            throw new MasterDataValidationException("Location ID is required");
        }
        validateLocation(location, false);
        location.setLocationId(id.trim());
        try {
            if (!locationDAO.existsById(id.trim())) {
                throw new MasterDataNotFoundException("Location not found: " + id);
            }
            locationDAO.update(location);
            return locationDAO.findById(id.trim()).orElse(location);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update location: " + e.getMessage(), e);
        }
    }

    @Override
    public void deactivateLocation(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new MasterDataValidationException("Location ID is required");
        }
        try {
            if (!locationDAO.existsById(id.trim())) {
                throw new MasterDataNotFoundException("Location not found: " + id);
            }
            locationDAO.deactivate(id.trim());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to deactivate location: " + e.getMessage(), e);
        }
    }

    private void validateLocation(Location location, boolean checkId) {
        if (location == null) {
            throw new MasterDataValidationException("Location payload cannot be empty");
        }
        if (checkId) {
            if (location.getLocationId() == null || location.getLocationId().trim().isEmpty()) {
                throw new MasterDataValidationException("Location ID is required");
            }
        }
        if (location.getLocationName() == null || location.getLocationName().trim().isEmpty()) {
            throw new MasterDataValidationException("Location Name is required");
        }
    }

    // ==========================================
    // CATEGORIES
    // ==========================================
    @Override
    public List<Category> getAllCategories(String active) {
        try {
            return categoryDAO.findAll(active);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch categories: " + e.getMessage(), e);
        }
    }

    @Override
    public Category getCategoryById(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new MasterDataValidationException("Category ID is required");
        }
        try {
            return categoryDAO.findById(id.trim())
                    .orElseThrow(() -> new MasterDataNotFoundException("Category not found: " + id));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch category: " + e.getMessage(), e);
        }
    }

    @Override
    public Category createCategory(Category category) {
        validateCategory(category, true);
        try {
            if (categoryDAO.existsById(category.getCategoryId().trim())) {
                throw new MasterDataConflictException("Category ID '" + category.getCategoryId().trim() + "' already exists");
            }
            categoryDAO.insert(category);
            return categoryDAO.findById(category.getCategoryId().trim()).orElse(category);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create category: " + e.getMessage(), e);
        }
    }

    @Override
    public Category updateCategory(String id, Category category) {
        if (id == null || id.trim().isEmpty()) {
            throw new MasterDataValidationException("Category ID is required");
        }
        validateCategory(category, false);
        category.setCategoryId(id.trim());
        try {
            if (!categoryDAO.existsById(id.trim())) {
                throw new MasterDataNotFoundException("Category not found: " + id);
            }
            categoryDAO.update(category);
            return categoryDAO.findById(id.trim()).orElse(category);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update category: " + e.getMessage(), e);
        }
    }

    @Override
    public void deactivateCategory(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new MasterDataValidationException("Category ID is required");
        }
        try {
            if (!categoryDAO.existsById(id.trim())) {
                throw new MasterDataNotFoundException("Category not found: " + id);
            }
            categoryDAO.deactivate(id.trim());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to deactivate category: " + e.getMessage(), e);
        }
    }

    private void validateCategory(Category category, boolean checkId) {
        if (category == null) {
            throw new MasterDataValidationException("Category payload cannot be empty");
        }
        if (checkId) {
            if (category.getCategoryId() == null || category.getCategoryId().trim().isEmpty()) {
                throw new MasterDataValidationException("Category ID is required");
            }
        }
        if (category.getCategoryName() == null || category.getCategoryName().trim().isEmpty()) {
            throw new MasterDataValidationException("Category Name is required");
        }
    }

    // ==========================================
    // OPTIONS FOR ASSET DROPDOWNS
    // ==========================================
    @Override
    public Map<String, List<String>> getMasterDataOptions() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        try {
            List<String> depts = departmentDAO.findAll("Y").stream()
                    .map(Department::getDepartmentId)
                    .collect(Collectors.toList());
            List<String> cats = categoryDAO.findAll("Y").stream()
                    .map(Category::getCategoryId)
                    .collect(Collectors.toList());
            List<String> locs = locationDAO.findAll("Y").stream()
                    .map(Location::getLocationId)
                    .collect(Collectors.toList());

            map.put("departments", depts);
            map.put("categories", cats);
            map.put("locations", locs);
            map.put("statuses", AssetConstants.STATUSES);
            return map;
        } catch (SQLException e) {
            // Fallback to constants if DB read error occurs
            return AssetConstants.getOptions();
        }
    }
}
