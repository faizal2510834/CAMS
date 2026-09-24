package com.cams.model;

import java.io.Serializable;

/**
 * Category-specific detail model for Computer assets.
 */
public class ComputerDetails implements Serializable {

    private static final long serialVersionUID = 1L;

    private String cpu;
    private String monitor;
    private String keyboard;
    private String mouse;
    private String printer;
    private String software;
    private String ipAddress;

    public ComputerDetails() {
    }

    public ComputerDetails(String cpu, String monitor, String keyboard, String mouse,
                           String printer, String software, String ipAddress) {
        this.cpu = cpu;
        this.monitor = monitor;
        this.keyboard = keyboard;
        this.mouse = mouse;
        this.printer = printer;
        this.software = software;
        this.ipAddress = ipAddress;
    }

    public String getCpu() {
        return cpu;
    }

    public void setCpu(String cpu) {
        this.cpu = cpu;
    }

    public String getMonitor() {
        return monitor;
    }

    public void setMonitor(String monitor) {
        this.monitor = monitor;
    }

    public String getKeyboard() {
        return keyboard;
    }

    public void setKeyboard(String keyboard) {
        this.keyboard = keyboard;
    }

    public String getMouse() {
        return mouse;
    }

    public void setMouse(String mouse) {
        this.mouse = mouse;
    }

    public String getPrinter() {
        return printer;
    }

    public void setPrinter(String printer) {
        this.printer = printer;
    }

    public String getSoftware() {
        return software;
    }

    public void setSoftware(String software) {
        this.software = software;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
}
