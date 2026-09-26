package com.cams.service;

import com.cams.model.Asset;
import com.cams.model.AssetDepreciation;
import com.cams.model.DepreciationSummary;

import java.sql.SQLException;

/**
 * Service contract for computing straight-line asset depreciation and campus valuations.
 */
public interface DepreciationService {

    /**
     * Useful life years constants per category.
     */
    int USEFUL_LIFE_COMPUTER = 4;
    int USEFUL_LIFE_LAB_EQUIPMENT = 7;
    int USEFUL_LIFE_CLASSROOM = 8;
    int USEFUL_LIFE_FURNITURE = 10;
    int USEFUL_LIFE_DEFAULT = 5;

    /**
     * Resolves useful life in years based on asset category name.
     */
    int getUsefulLifeYears(String category);

    /**
     * Calculates depreciation metrics and schedule for a single asset.
     */
    AssetDepreciation getAssetDepreciation(String assetId) throws SQLException, AssetNotFoundException;

    /**
     * Calculates depreciation metrics and schedule on a pre-fetched Asset object.
     */
    AssetDepreciation calculateDepreciation(Asset asset);

    /**
     * Aggregates current valuation, original cost, and depreciation across all active campus assets.
     */
    DepreciationSummary getDepreciationSummary() throws SQLException;
}
