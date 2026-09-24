package com.cams.service;

import com.cams.dao.AssetDAO;
import com.cams.dao.AssetDAOImpl;
import com.cams.dao.ClassroomDetailsDAO;
import com.cams.dao.ClassroomDetailsDAOImpl;
import com.cams.dao.ComputerDetailsDAO;
import com.cams.dao.ComputerDetailsDAOImpl;
import com.cams.dao.FurnitureDetailsDAO;
import com.cams.dao.FurnitureDetailsDAOImpl;
import com.cams.dao.LabDetailsDAO;
import com.cams.dao.LabDetailsDAOImpl;
import com.cams.dao.DepartmentDAO;
import com.cams.dao.DepartmentDAOImpl;
import com.cams.dao.LocationDAO;
import com.cams.dao.LocationDAOImpl;
import com.cams.dao.CategoryDAO;
import com.cams.dao.CategoryDAOImpl;
import com.cams.model.Asset;
import com.cams.model.AssetQueryCriteria;
import com.cams.model.ClassroomDetails;
import com.cams.model.ComputerDetails;
import com.cams.model.FurnitureDetails;
import com.cams.model.LabDetails;
import com.cams.model.PagedResult;
import com.cams.util.AssetConstants;
import com.cams.util.DBConnection;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Implementation of AssetService managing transaction lifecycles, business rules,
 * status transitions, category-specific details, format validation, and duplicate checking.
 */
public class AssetServiceImpl implements AssetService {

    private static final Logger LOGGER = Logger.getLogger(AssetServiceImpl.class.getName());

    private static final Pattern ASSET_ID_PATTERN = Pattern.compile("^[A-Z0-9-]+$");

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    // Allowed status transitions
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "AVAILABLE", Set.of("ISSUED", "UNDER_MAINTENANCE", "DISPOSED"),
            "ISSUED", Set.of("AVAILABLE", "UNDER_MAINTENANCE"),
            "UNDER_MAINTENANCE", Set.of("AVAILABLE", "DISPOSED"),
            "DISPOSED", Set.of() // Terminal
    );

    // Allowed shape keys (normalized lowercase without underscores)
    private static final Set<String> COMPUTER_KEYS = Set.of(
            "cpu", "monitor", "keyboard", "mouse", "printer", "software", "ipaddress"
    );

    private static final Set<String> CLASSROOM_KEYS = Set.of(
            "furnituredesc", "projector", "smartboard", "ac", "seatingcapacity"
    );

    private static final Set<String> LAB_KEYS = Set.of(
            "equipmenttype", "equipmentcondition", "condition", "lastcalibrationdate", "calibrationdate"
    );

    private static final Set<String> FURNITURE_KEYS = Set.of(
            "furnituretype", "material", "quantity"
    );

    private final AssetDAO assetDAO;
    private final ComputerDetailsDAO computerDetailsDAO;
    private final ClassroomDetailsDAO classroomDetailsDAO;
    private final LabDetailsDAO labDetailsDAO;
    private final FurnitureDetailsDAO furnitureDetailsDAO;
    private final DepartmentDAO departmentDAO;
    private final LocationDAO locationDAO;
    private final CategoryDAO categoryDAO;
    private final MasterDataService masterDataService;

    public AssetServiceImpl() {
        this(new AssetDAOImpl(),
             new ComputerDetailsDAOImpl(),
             new ClassroomDetailsDAOImpl(),
             new LabDetailsDAOImpl(),
             new FurnitureDetailsDAOImpl(),
             new DepartmentDAOImpl(),
             new LocationDAOImpl(),
             new CategoryDAOImpl(),
             new MasterDataServiceImpl());
    }

    public AssetServiceImpl(AssetDAO assetDAO) {
        this(assetDAO,
             new ComputerDetailsDAOImpl(),
             new ClassroomDetailsDAOImpl(),
             new LabDetailsDAOImpl(),
             new FurnitureDetailsDAOImpl(),
             new DepartmentDAOImpl(),
             new LocationDAOImpl(),
             new CategoryDAOImpl(),
             new MasterDataServiceImpl());
    }

    public AssetServiceImpl(AssetDAO assetDAO,
                            ComputerDetailsDAO computerDetailsDAO,
                            ClassroomDetailsDAO classroomDetailsDAO,
                            LabDetailsDAO labDetailsDAO,
                            FurnitureDetailsDAO furnitureDetailsDAO) {
        this(assetDAO,
             computerDetailsDAO,
             classroomDetailsDAO,
             labDetailsDAO,
             furnitureDetailsDAO,
             new DepartmentDAOImpl(),
             new LocationDAOImpl(),
             new CategoryDAOImpl(),
             new MasterDataServiceImpl());
    }

    public AssetServiceImpl(AssetDAO assetDAO,
                            ComputerDetailsDAO computerDetailsDAO,
                            ClassroomDetailsDAO classroomDetailsDAO,
                            LabDetailsDAO labDetailsDAO,
                            FurnitureDetailsDAO furnitureDetailsDAO,
                            DepartmentDAO departmentDAO,
                            LocationDAO locationDAO,
                            CategoryDAO categoryDAO,
                            MasterDataService masterDataService) {
        this.assetDAO = assetDAO;
        this.computerDetailsDAO = computerDetailsDAO;
        this.classroomDetailsDAO = classroomDetailsDAO;
        this.labDetailsDAO = labDetailsDAO;
        this.furnitureDetailsDAO = furnitureDetailsDAO;
        this.departmentDAO = departmentDAO;
        this.locationDAO = locationDAO;
        this.categoryDAO = categoryDAO;
        this.masterDataService = masterDataService;
    }

    @Override
    public Asset getAssetById(String assetId) throws AssetNotFoundException, SQLException {
        if (assetId == null || assetId.trim().isEmpty()) {
            throw new AssetNotFoundException("Asset ID cannot be empty");
        }
        Asset asset = assetDAO.findById(assetId.trim())
                .orElseThrow(() -> new AssetNotFoundException("Asset not found with ID: " + assetId.trim()));

        loadDetails(asset);
        return asset;
    }

    @Override
    public PagedResult<Asset> getAssets(AssetQueryCriteria criteria) throws SQLException {
        if (criteria == null) {
            criteria = new AssetQueryCriteria();
        }
        return assetDAO.search(criteria);
    }

    @Override
    public Asset addAsset(Asset asset) throws AssetValidationException, AssetConflictException, SQLException {
        return createAsset(asset);
    }

    @Override
    public Asset createAsset(Asset asset) throws AssetValidationException, AssetConflictException, SQLException {
        if (asset == null) {
            throw new AssetValidationException("Asset payload cannot be null");
        }

        // 1. Validate ASSET_ID format: ^[A-Z0-9-]+$
        if (asset.getAssetId() == null || asset.getAssetId().trim().isEmpty()) {
            throw new AssetValidationException("Asset ID is required");
        }
        String normalizedId = asset.getAssetId().trim().toUpperCase();
        if (!ASSET_ID_PATTERN.matcher(normalizedId).matches()) {
            throw new AssetValidationException("Asset ID '" + normalizedId + "' is invalid. Must match pattern ^[A-Z0-9-]+$");
        }
        asset.setAssetId(normalizedId);

        // 2. Reject duplicate at Service level
        if (assetDAO.findById(normalizedId).isPresent()) {
            throw new AssetConflictException("Duplicate Asset ID: Asset '" + normalizedId + "' already exists");
        }

        // 3. Validate mandatory fields
        if (asset.getAssetName() == null || asset.getAssetName().trim().isEmpty()) {
            throw new AssetValidationException("Asset Name is required");
        }
        asset.setAssetName(asset.getAssetName().trim());

        try {
            if (!categoryDAO.isActive(asset.getCategory())) {
                throw new AssetValidationException("Invalid Category: '" + asset.getCategory() + "'. Category must be an active master data category.");
            }
            if (!departmentDAO.isActive(asset.getDepartment())) {
                throw new AssetValidationException("Invalid Department: '" + asset.getDepartment() + "'. Department must be an active master data department.");
            }
            if (!locationDAO.isActive(asset.getLocation())) {
                throw new AssetValidationException("Invalid Location: '" + asset.getLocation() + "'. Location must be an active master data location.");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error validating master data: " + e.getMessage(), e);
        }

        if (asset.getPurchaseDate() == null) {
            throw new AssetValidationException("Purchase date is required");
        }

        // 4. PURCHASE_COST must be > 0
        if (asset.getPurchaseCost() == null || asset.getPurchaseCost().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AssetValidationException("Purchase cost must be greater than zero");
        }

        // 5. WARRANTY_EXPIRY must be >= PURCHASE_DATE if provided
        if (asset.getWarrantyExpiry() != null && asset.getWarrantyExpiry().before(asset.getPurchaseDate())) {
            throw new AssetValidationException("Warranty expiry date cannot be earlier than purchase date");
        }

        // 6. Default status to AVAILABLE
        asset.setStatus("AVAILABLE");

        // 7. Transactional creation: insert asset, insert details, commit
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                boolean created = assetDAO.create(conn, asset);
                if (!created) {
                    throw new SQLException("Failed to insert asset record");
                }

                // Process category-specific details inside transaction
                if (!"Other".equalsIgnoreCase(asset.getCategory()) && asset.getDetails() != null) {
                    Object parsedDetails = validateAndParseDetails(asset.getCategory(), asset.getDetails());
                    saveCategoryDetails(conn, normalizedId, asset.getCategory(), parsedDetails);
                    asset.setDetails(parsedDetails);
                } else if ("Other".equalsIgnoreCase(asset.getCategory())) {
                    asset.setDetails(null);
                }

                conn.commit();
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rbEx) {
                    LOGGER.log(Level.WARNING, "Failed to rollback create transaction", rbEx);
                }
                if (e instanceof AssetValidationException) {
                    throw (AssetValidationException) e;
                }
                if (e instanceof AssetConflictException) {
                    throw (AssetConflictException) e;
                }
                if (e instanceof SQLException) {
                    SQLException sqlEx = (SQLException) e;
                    if (sqlEx.getMessage() != null && sqlEx.getMessage().contains("ORA-00001")) {
                        throw new AssetConflictException("Duplicate Asset ID: Asset '" + normalizedId + "' already exists in database");
                    }
                    throw sqlEx;
                }
                throw new RuntimeException("Unexpected error during asset creation", e);
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {}
            }
        }

        try {
            return getAssetById(normalizedId);
        } catch (AssetNotFoundException e) {
            return asset;
        }
    }

    @Override
    public Asset updateAsset(String assetId, Asset updateData)
            throws AssetValidationException, AssetConflictException, AssetNotFoundException, SQLException {

        if (assetId == null || assetId.trim().isEmpty()) {
            throw new AssetValidationException("Asset ID is required for update");
        }
        if (updateData == null) {
            throw new AssetValidationException("Update data cannot be null");
        }

        Asset existing = getAssetById(assetId.trim());

        // Editing a DISPOSED asset returns 409 Conflict
        if ("DISPOSED".equalsIgnoreCase(existing.getStatus())) {
            throw new AssetConflictException("Cannot edit asset '" + assetId.trim() + "' because it is DISPOSED (retired)");
        }

        // Validate editable fields
        if (updateData.getAssetName() == null || updateData.getAssetName().trim().isEmpty()) {
            throw new AssetValidationException("Asset Name is required");
        }
        try {
            if (!categoryDAO.isActive(updateData.getCategory())) {
                throw new AssetValidationException("Invalid Category: '" + updateData.getCategory() + "'. Category must be an active master data category.");
            }
            if (!departmentDAO.isActive(updateData.getDepartment())) {
                throw new AssetValidationException("Invalid Department: '" + updateData.getDepartment() + "'. Department must be an active master data department.");
            }
            if (!locationDAO.isActive(updateData.getLocation())) {
                throw new AssetValidationException("Invalid Location: '" + updateData.getLocation() + "'. Location must be an active master data location.");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error validating master data: " + e.getMessage(), e);
        }
        if (updateData.getPurchaseDate() == null) {
            throw new AssetValidationException("Purchase date is required");
        }
        if (updateData.getPurchaseCost() == null || updateData.getPurchaseCost().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AssetValidationException("Purchase cost must be greater than zero");
        }
        if (updateData.getWarrantyExpiry() != null && updateData.getWarrantyExpiry().before(updateData.getPurchaseDate())) {
            throw new AssetValidationException("Warranty expiry date cannot be earlier than purchase date");
        }

        // asset_id and status are NOT changeable via PUT
        updateData.setAssetId(existing.getAssetId());
        updateData.setStatus(existing.getStatus());

        // Transactional update: update asset, update details, commit
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                boolean updated = assetDAO.update(conn, updateData);
                if (!updated) {
                    throw new SQLException("Failed to update asset: " + assetId);
                }

                // If category changed, clean up previous category details row
                if (existing.getCategory() != null && !existing.getCategory().equalsIgnoreCase(updateData.getCategory())) {
                    deleteDetailsForCategory(conn, updateData.getAssetId(), existing.getCategory());
                }

                // Process details update
                if (!"Other".equalsIgnoreCase(updateData.getCategory())) {
                    Object rawDetails = updateData.getDetails();
                    if (rawDetails != null) {
                        Object parsedDetails = validateAndParseDetails(updateData.getCategory(), rawDetails);
                        saveCategoryDetails(conn, updateData.getAssetId(), updateData.getCategory(), parsedDetails);
                        updateData.setDetails(parsedDetails);
                    }
                } else {
                    deleteDetailsForCategory(conn, updateData.getAssetId(), existing.getCategory());
                    updateData.setDetails(null);
                }

                conn.commit();
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rbEx) {
                    LOGGER.log(Level.WARNING, "Failed to rollback update transaction", rbEx);
                }
                if (e instanceof AssetValidationException) {
                    throw (AssetValidationException) e;
                }
                if (e instanceof AssetConflictException) {
                    throw (AssetConflictException) e;
                }
                if (e instanceof SQLException) {
                    throw (SQLException) e;
                }
                throw new RuntimeException("Unexpected error updating asset", e);
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {}
            }
        }

        return getAssetById(existing.getAssetId());
    }

    @Override
    public void retireAsset(String assetId, String reason)
            throws AssetConflictException, AssetNotFoundException, SQLException {

        Asset existing = getAssetById(assetId);

        // Retiring an ISSUED asset returns 409
        if ("ISSUED".equalsIgnoreCase(existing.getStatus())) {
            throw new AssetConflictException("Cannot retire asset '" + assetId + "': Asset is currently ISSUED. Return it to inventory first.");
        }

        // Retiring an already-DISPOSED asset returns 409
        if ("DISPOSED".equalsIgnoreCase(existing.getStatus())) {
            throw new AssetConflictException("Asset '" + assetId + "' is already retired/disposed");
        }

        Date today = new Date(System.currentTimeMillis());
        String finalReason = (reason != null && !reason.trim().isEmpty())
                ? reason.trim()
                : "Retired from operational inventory";

        boolean retired = assetDAO.retire(assetId.trim(), today, finalReason);
        if (!retired) {
            throw new SQLException("Failed to retire asset: " + assetId);
        }
    }

    @Override
    public void changeStatus(Connection con, String assetId, String expectedStatus, String newStatus)
            throws AssetConflictException, SQLException {

        if (assetId == null || expectedStatus == null || newStatus == null) {
            throw new IllegalArgumentException("Asset ID, expected status, and new status must not be null");
        }

        String from = expectedStatus.trim().toUpperCase();
        String to = newStatus.trim().toUpperCase();

        if (!AssetConstants.isValidStatus(to)) {
            throw new AssetConflictException("Target status '" + to + "' is not a recognized asset status");
        }

        // Validate allowed state transitions
        Set<String> allowedTargets = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowedTargets.contains(to)) {
            throw new AssetConflictException("Invalid status transition: Cannot transition asset from '" + from + "' to '" + to + "'");
        }

        int affected = assetDAO.changeStatus(con, assetId.trim(), from, to);
        if (affected == 0) {
            throw new AssetConflictException("Status transition conflict: Asset '" + assetId.trim() +
                    "' was expected to be in status '" + from + "', but 0 rows were updated. Current status has changed.");
        }
    }

    @Override
    public Map<String, List<String>> getOptions() {
        return masterDataService.getMasterDataOptions();
    }

    // -------------------------------------------------------------------------
    // Helper Methods: Details Loading, Parsing, Validation, and Persistence
    // -------------------------------------------------------------------------

    private void loadDetails(Asset asset) throws SQLException {
        if (asset == null || asset.getCategory() == null) {
            if (asset != null) asset.setDetails(null);
            return;
        }

        try (Connection conn = DBConnection.getConnection()) {
            switch (asset.getCategory().trim()) {
                case "Computer":
                    asset.setDetails(computerDetailsDAO.findByAssetId(conn, asset.getAssetId()).orElse(null));
                    break;
                case "Classroom Asset":
                    asset.setDetails(classroomDetailsDAO.findByAssetId(conn, asset.getAssetId()).orElse(null));
                    break;
                case "Laboratory Equipment":
                    asset.setDetails(labDetailsDAO.findByAssetId(conn, asset.getAssetId()).orElse(null));
                    break;
                case "Furniture":
                    asset.setDetails(furnitureDetailsDAO.findByAssetId(conn, asset.getAssetId()).orElse(null));
                    break;
                case "Other":
                default:
                    asset.setDetails(null);
                    break;
            }
        }
    }

    private void saveCategoryDetails(Connection conn, String assetId, String category, Object details) throws SQLException {
        if (details == null || category == null) return;
        switch (category.trim()) {
            case "Computer":
                if (details instanceof ComputerDetails) {
                    computerDetailsDAO.insertOrUpdate(conn, assetId, (ComputerDetails) details);
                }
                break;
            case "Classroom Asset":
                if (details instanceof ClassroomDetails) {
                    classroomDetailsDAO.insertOrUpdate(conn, assetId, (ClassroomDetails) details);
                }
                break;
            case "Laboratory Equipment":
                if (details instanceof LabDetails) {
                    labDetailsDAO.insertOrUpdate(conn, assetId, (LabDetails) details);
                }
                break;
            case "Furniture":
                if (details instanceof FurnitureDetails) {
                    furnitureDetailsDAO.insertOrUpdate(conn, assetId, (FurnitureDetails) details);
                }
                break;
            case "Other":
            default:
                break;
        }
    }

    private void deleteDetailsForCategory(Connection conn, String assetId, String category) throws SQLException {
        if (category == null || assetId == null) return;
        String tableName = null;
        switch (category.trim()) {
            case "Computer": tableName = "COMPUTER_DETAILS"; break;
            case "Classroom Asset": tableName = "CLASSROOM_DETAILS"; break;
            case "Laboratory Equipment": tableName = "LAB_DETAILS"; break;
            case "Furniture": tableName = "FURNITURE_DETAILS"; break;
            default: break;
        }
        if (tableName != null) {
            String sql = "DELETE FROM " + tableName + " WHERE ASSET_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, assetId.trim());
                ps.executeUpdate();
            }
        }
    }

    /**
     * Validates the detail payload shape against the declared category and returns
     * the strongly-typed detail model.
     * Throws AssetValidationException (HTTP 400) on shape mismatch or field format error.
     */
    public Object validateAndParseDetails(String category, Object payload) throws AssetValidationException {
        if (payload == null) {
            return null;
        }

        if ("Other".equalsIgnoreCase(category)) {
            return null;
        }

        // If already converted to target type
        if (payload instanceof ComputerDetails && "Computer".equalsIgnoreCase(category)) {
            validateComputerDetails((ComputerDetails) payload);
            return payload;
        }
        if (payload instanceof ClassroomDetails && "Classroom Asset".equalsIgnoreCase(category)) {
            validateClassroomDetails((ClassroomDetails) payload);
            return payload;
        }
        if (payload instanceof LabDetails && "Laboratory Equipment".equalsIgnoreCase(category)) {
            return payload;
        }
        if (payload instanceof FurnitureDetails && "Furniture".equalsIgnoreCase(category)) {
            validateFurnitureDetails((FurnitureDetails) payload);
            return payload;
        }

        // Convert generic payload (JsonObject or Map) to a uniform Map<String, Object>
        Map<String, Object> rawMap = toPropertyMap(payload);

        // Check for shape mismatch: every key in rawMap must be valid for the declared category
        Set<String> allowedKeys;
        switch (category.trim()) {
            case "Computer":
                allowedKeys = COMPUTER_KEYS;
                break;
            case "Classroom Asset":
                allowedKeys = CLASSROOM_KEYS;
                break;
            case "Laboratory Equipment":
                allowedKeys = LAB_KEYS;
                break;
            case "Furniture":
                allowedKeys = FURNITURE_KEYS;
                break;
            default:
                throw new AssetValidationException("Unsupported category for details: " + category);
        }

        for (String key : rawMap.keySet()) {
            String norm = key.toLowerCase().replace("_", "").trim();
            if (!allowedKeys.contains(norm)) {
                throw new AssetValidationException("Details shape mismatch: property '" + key +
                        "' is not allowed for category '" + category + "'");
            }
        }

        // Build typed object and validate fields
        switch (category.trim()) {
            case "Computer":
                ComputerDetails comp = new ComputerDetails();
                for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                    String k = entry.getKey().toLowerCase().replace("_", "").trim();
                    Object v = entry.getValue();
                    switch (k) {
                        case "cpu": comp.setCpu(getString(v)); break;
                        case "monitor": comp.setMonitor(getString(v)); break;
                        case "keyboard": comp.setKeyboard(getString(v)); break;
                        case "mouse": comp.setMouse(getString(v)); break;
                        case "printer": comp.setPrinter(getString(v)); break;
                        case "software": comp.setSoftware(getString(v)); break;
                        case "ipaddress":
                            String ip = getString(v);
                            if (ip != null && !ip.isEmpty()) {
                                if (!IPV4_PATTERN.matcher(ip).matches()) {
                                    throw new AssetValidationException("Invalid IPv4 address format for ip_address: '" + ip + "'");
                                }
                                comp.setIpAddress(ip);
                            }
                            break;
                    }
                }
                return comp;

            case "Classroom Asset":
                ClassroomDetails cls = new ClassroomDetails();
                for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                    String k = entry.getKey().toLowerCase().replace("_", "").trim();
                    Object v = entry.getValue();
                    switch (k) {
                        case "furnituredesc": cls.setFurnitureDesc(getString(v)); break;
                        case "projector": cls.setProjector(getString(v)); break;
                        case "smartboard": cls.setSmartBoard(getString(v)); break;
                        case "ac": cls.setAc(getString(v)); break;
                        case "seatingcapacity":
                            cls.setSeatingCapacity(getPositiveInteger("seating_capacity", v));
                            break;
                    }
                }
                return cls;

            case "Laboratory Equipment":
                LabDetails lab = new LabDetails();
                for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                    String k = entry.getKey().toLowerCase().replace("_", "").trim();
                    Object v = entry.getValue();
                    switch (k) {
                        case "equipmenttype": lab.setEquipmentType(getString(v)); break;
                        case "condition":
                        case "equipmentcondition": lab.setCondition(getString(v)); break;
                        case "lastcalibrationdate":
                        case "calibrationdate":
                            lab.setLastCalibrationDate(getDate("last_calibration_date", v));
                            break;
                    }
                }
                return lab;

            case "Furniture":
                FurnitureDetails furn = new FurnitureDetails();
                for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                    String k = entry.getKey().toLowerCase().replace("_", "").trim();
                    Object v = entry.getValue();
                    switch (k) {
                        case "furnituretype": furn.setFurnitureType(getString(v)); break;
                        case "material": furn.setMaterial(getString(v)); break;
                        case "quantity":
                            furn.setQuantity(getPositiveInteger("quantity", v));
                            break;
                    }
                }
                return furn;

            default:
                return null;
        }
    }

    private void validateComputerDetails(ComputerDetails details) throws AssetValidationException {
        if (details.getIpAddress() != null && !details.getIpAddress().trim().isEmpty()) {
            if (!IPV4_PATTERN.matcher(details.getIpAddress().trim()).matches()) {
                throw new AssetValidationException("Invalid IPv4 address format for ip_address: '" + details.getIpAddress() + "'");
            }
        }
    }

    private void validateClassroomDetails(ClassroomDetails details) throws AssetValidationException {
        if (details.getSeatingCapacity() != null && details.getSeatingCapacity() <= 0) {
            throw new AssetValidationException("seating_capacity must be a positive integer (> 0)");
        }
    }

    private void validateFurnitureDetails(FurnitureDetails details) throws AssetValidationException {
        if (details.getQuantity() != null && details.getQuantity() <= 0) {
            throw new AssetValidationException("quantity must be a positive integer (> 0)");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toPropertyMap(Object payload) throws AssetValidationException {
        Map<String, Object> map = new HashMap<>();

        if (payload instanceof JsonObject) {
            JsonObject json = (JsonObject) payload;
            for (String key : json.keySet()) {
                JsonElement elem = json.get(key);
                if (elem == null || elem.isJsonNull()) {
                    map.put(key, null);
                } else if (elem.isJsonPrimitive()) {
                    JsonPrimitive prim = elem.getAsJsonPrimitive();
                    if (prim.isNumber()) {
                        map.put(key, prim.getAsNumber());
                    } else if (prim.isBoolean()) {
                        map.put(key, prim.getAsBoolean());
                    } else {
                        map.put(key, prim.getAsString());
                    }
                } else {
                    map.put(key, elem.toString());
                }
            }
        } else if (payload instanceof Map) {
            map.putAll((Map<String, Object>) payload);
        } else {
            throw new AssetValidationException("Details payload must be an object");
        }

        return map;
    }

    private String getString(Object val) {
        if (val == null) return null;
        String s = val.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private Integer getPositiveInteger(String fieldName, Object val) throws AssetValidationException {
        if (val == null) return null;
        if (val instanceof String && ((String) val).trim().isEmpty()) return null;

        try {
            int num;
            if (val instanceof Number) {
                num = ((Number) val).intValue();
            } else {
                num = Integer.parseInt(val.toString().trim());
            }
            if (num <= 0) {
                throw new AssetValidationException(fieldName + " must be a positive integer (> 0)");
            }
            return num;
        } catch (NumberFormatException e) {
            throw new AssetValidationException(fieldName + " must be a valid positive integer");
        }
    }

    private Date getDate(String fieldName, Object val) throws AssetValidationException {
        if (val == null) return null;
        if (val instanceof Date) return (Date) val;
        if (val instanceof java.util.Date) return new Date(((java.util.Date) val).getTime());

        String s = val.toString().trim();
        if (s.isEmpty()) return null;

        try {
            return Date.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new AssetValidationException("Invalid date format for " + fieldName + ": expected YYYY-MM-DD");
        }
    }
}
