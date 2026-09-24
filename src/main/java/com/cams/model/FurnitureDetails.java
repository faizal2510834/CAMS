package com.cams.model;

import java.io.Serializable;

/**
 * Category-specific detail model for Furniture assets.
 */
public class FurnitureDetails implements Serializable {

    private static final long serialVersionUID = 1L;

    private String furnitureType;
    private String material;
    private Integer quantity;

    public FurnitureDetails() {
    }

    public FurnitureDetails(String furnitureType, String material, Integer quantity) {
        this.furnitureType = furnitureType;
        this.material = material;
        this.quantity = quantity;
    }

    public String getFurnitureType() {
        return furnitureType;
    }

    public void setFurnitureType(String furnitureType) {
        this.furnitureType = furnitureType;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
