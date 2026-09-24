package com.cams.model;

import java.io.Serializable;

/**
 * Category-specific detail model for Classroom assets.
 */
public class ClassroomDetails implements Serializable {

    private static final long serialVersionUID = 1L;

    private String furnitureDesc;
    private String projector;
    private String smartBoard;
    private String ac;
    private Integer seatingCapacity;

    public ClassroomDetails() {
    }

    public ClassroomDetails(String furnitureDesc, String projector, String smartBoard,
                            String ac, Integer seatingCapacity) {
        this.furnitureDesc = furnitureDesc;
        this.projector = projector;
        this.smartBoard = smartBoard;
        this.ac = ac;
        this.seatingCapacity = seatingCapacity;
    }

    public String getFurnitureDesc() {
        return furnitureDesc;
    }

    public void setFurnitureDesc(String furnitureDesc) {
        this.furnitureDesc = furnitureDesc;
    }

    public String getProjector() {
        return projector;
    }

    public void setProjector(String projector) {
        this.projector = projector;
    }

    public String getSmartBoard() {
        return smartBoard;
    }

    public void setSmartBoard(String smartBoard) {
        this.smartBoard = smartBoard;
    }

    public String getAc() {
        return ac;
    }

    public void setAc(String ac) {
        this.ac = ac;
    }

    public Integer getSeatingCapacity() {
        return seatingCapacity;
    }

    public void setSeatingCapacity(Integer seatingCapacity) {
        this.seatingCapacity = seatingCapacity;
    }
}
