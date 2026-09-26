package com.cams.service;

import com.cams.dao.AssetDAO;
import com.cams.dao.AssetDAOImpl;
import com.cams.model.Asset;
import com.cams.model.AssetDepreciation;
import com.cams.model.DepreciationCategorySummary;
import com.cams.model.DepreciationScheduleItem;
import com.cams.model.DepreciationSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of straight-line depreciation calculations and valuations.
 */
public class DepreciationServiceImpl implements DepreciationService {

    private final AssetDAO assetDAO;

    public DepreciationServiceImpl() {
        this(new AssetDAOImpl());
    }

    public DepreciationServiceImpl(AssetDAO assetDAO) {
        this.assetDAO = assetDAO;
    }

    @Override
    public int getUsefulLifeYears(String category) {
        if (category == null || category.trim().isEmpty()) {
            return USEFUL_LIFE_DEFAULT;
        }
        String normalized = category.trim().toLowerCase();
        if (normalized.contains("computer")) {
            return USEFUL_LIFE_COMPUTER; // 4 years
        } else if (normalized.contains("laboratory") || normalized.contains("lab")) {
            return USEFUL_LIFE_LAB_EQUIPMENT; // 7 years
        } else if (normalized.contains("classroom")) {
            return USEFUL_LIFE_CLASSROOM; // 8 years
        } else if (normalized.contains("furniture")) {
            return USEFUL_LIFE_FURNITURE; // 10 years
        } else {
            return USEFUL_LIFE_DEFAULT; // 5 years fallback
        }
    }

    @Override
    public AssetDepreciation getAssetDepreciation(String assetId) throws SQLException, AssetNotFoundException {
        if (assetId == null || assetId.trim().isEmpty()) {
            throw new AssetNotFoundException("Asset ID cannot be empty");
        }
        Optional<Asset> assetOpt = assetDAO.findById(assetId.trim());
        if (!assetOpt.isPresent()) {
            throw new AssetNotFoundException("Asset '" + assetId + "' not found");
        }
        return calculateDepreciation(assetOpt.get());
    }

    @Override
    public AssetDepreciation calculateDepreciation(Asset asset) {
        AssetDepreciation dep = new AssetDepreciation();
        dep.setAssetId(asset.getAssetId());
        dep.setAssetName(asset.getAssetName());
        dep.setCategory(asset.getCategory());
        dep.setDepartment(asset.getDepartment());
        dep.setPurchaseDate(asset.getPurchaseDate());

        BigDecimal cost = asset.getPurchaseCost() != null ? asset.getPurchaseCost() : BigDecimal.ZERO;
        dep.setPurchaseCost(cost);

        int usefulLife = getUsefulLifeYears(asset.getCategory());
        dep.setUsefulLifeYears(usefulLife);

        BigDecimal annualDep = cost.divide(BigDecimal.valueOf(usefulLife), 2, RoundingMode.HALF_UP);
        dep.setAnnualDepreciation(annualDep);

        LocalDate today = LocalDate.now();
        LocalDate pDate = asset.getPurchaseDate() != null ? asset.getPurchaseDate().toLocalDate() : today;

        double yearsElapsed = 0.0;
        if (pDate.isBefore(today)) {
            long days = ChronoUnit.DAYS.between(pDate, today);
            yearsElapsed = Math.round((days / 365.25) * 100.0) / 100.0;
        }
        dep.setYearsElapsed(yearsElapsed);

        BigDecimal accumulated;
        if (yearsElapsed >= usefulLife) {
            accumulated = cost;
        } else {
            accumulated = annualDep.multiply(BigDecimal.valueOf(yearsElapsed)).setScale(2, RoundingMode.HALF_UP);
            if (accumulated.compareTo(cost) > 0) {
                accumulated = cost;
            }
        }
        dep.setAccumulatedDepreciation(accumulated);

        BigDecimal residual = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        dep.setResidualValue(residual);

        BigDecimal currentVal = cost.subtract(accumulated);
        if (currentVal.compareTo(residual) < 0) {
            currentVal = residual;
        }
        dep.setCurrentValue(currentVal);

        // Build yearly schedule
        List<DepreciationScheduleItem> schedule = new ArrayList<>();
        // Year 0
        schedule.add(new DepreciationScheduleItem(0, pDate.toString(),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                cost));

        BigDecimal prevAccum = BigDecimal.ZERO;
        for (int y = 1; y <= usefulLife; y++) {
            LocalDate yearDate = pDate.plusYears(y);
            BigDecimal accumYear;
            BigDecimal expense;

            if (y == usefulLife) {
                // Final year adjustment to reach exact cost
                accumYear = cost;
                expense = cost.subtract(prevAccum);
            } else {
                accumYear = annualDep.multiply(BigDecimal.valueOf(y)).setScale(2, RoundingMode.HALF_UP);
                if (accumYear.compareTo(cost) > 0) {
                    accumYear = cost;
                }
                expense = accumYear.subtract(prevAccum);
            }

            BigDecimal endingValue = cost.subtract(accumYear);
            if (endingValue.compareTo(BigDecimal.ZERO) < 0) {
                endingValue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }

            schedule.add(new DepreciationScheduleItem(y, yearDate.toString(), expense, accumYear, endingValue));
            prevAccum = accumYear;
        }

        dep.setSchedule(schedule);
        return dep;
    }

    @Override
    public DepreciationSummary getDepreciationSummary() throws SQLException {
        List<Asset> activeAssets = assetDAO.findAllActiveForDepreciation();

        DepreciationSummary summary = new DepreciationSummary();
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalCurrent = BigDecimal.ZERO;
        BigDecimal totalDepreciated = BigDecimal.ZERO;

        Map<String, List<AssetDepreciation>> byCategory = new HashMap<>();

        for (Asset asset : activeAssets) {
            AssetDepreciation dep = calculateDepreciation(asset);
            totalCost = totalCost.add(dep.getPurchaseCost());
            totalCurrent = totalCurrent.add(dep.getCurrentValue());
            totalDepreciated = totalDepreciated.add(dep.getAccumulatedDepreciation());

            String cat = dep.getCategory() != null ? dep.getCategory() : "Unassigned";
            byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(dep);
        }

        summary.setTotalPurchaseCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        summary.setTotalCurrentValue(totalCurrent.setScale(2, RoundingMode.HALF_UP));
        summary.setTotalAccumulatedDepreciation(totalDepreciated.setScale(2, RoundingMode.HALF_UP));
        summary.setAssetCount(activeAssets.size());

        List<DepreciationCategorySummary> categoryBreakdown = new ArrayList<>();
        for (Map.Entry<String, List<AssetDepreciation>> entry : byCategory.entrySet()) {
            String cat = entry.getKey();
            List<AssetDepreciation> items = entry.getValue();

            BigDecimal catCost = BigDecimal.ZERO;
            BigDecimal catVal = BigDecimal.ZERO;
            BigDecimal catDep = BigDecimal.ZERO;

            for (AssetDepreciation d : items) {
                catCost = catCost.add(d.getPurchaseCost());
                catVal = catVal.add(d.getCurrentValue());
                catDep = catDep.add(d.getAccumulatedDepreciation());
            }

            categoryBreakdown.add(new DepreciationCategorySummary(cat, items.size(),
                    catCost.setScale(2, RoundingMode.HALF_UP),
                    catVal.setScale(2, RoundingMode.HALF_UP),
                    catDep.setScale(2, RoundingMode.HALF_UP)));
        }

        categoryBreakdown.sort(Comparator.comparing(DepreciationCategorySummary::getCategory));
        summary.setCategoryBreakdown(categoryBreakdown);

        return summary;
    }
}
