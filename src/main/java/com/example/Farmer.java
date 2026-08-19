package com.example;

import java.util.Arrays;
import java.util.Scanner;

/** Stores account and financial information for a farmer. */
public class Farmer {

    private String username;
    private char[] password;
    private String cropType;
    private double lastYearsNetProfit;

    /** Creates a farmer profile with account, crop, and profit information. */
    public Farmer(String username, char[] password, String cropType, double lastYearsNetProfit) {
        setUsername(username);
        setPassword(password);
        setCropType(cropType);
        setLastYearsNetProfit(lastYearsNetProfit);
    }

    /** Collects one farmer profile from the command line. */
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Username: ");
            String username = scanner.nextLine();
            System.out.print("Password: ");
            char[] password = scanner.nextLine().toCharArray();
            System.out.print("Crop type: ");
            String cropType = scanner.nextLine();
            System.out.print("Last year's net profit: ");
            double netProfit = Double.parseDouble(scanner.nextLine());

            Farmer farmer = new Farmer(username, password, cropType, netProfit);
            System.out.println("Farmer profile stored for " + farmer.getUsername());
            System.out.println("Crop type: " + farmer.getCropType());
            System.out.println("Last year's net profit: " + farmer.getLastYearsNetProfit());
            farmer.clearPassword();
        }
    }

    /** Returns the farmer's username. */
    public String getUsername() {
        return username;
    }

    /** Updates the farmer's username. */
    public void setUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
        this.username = username;
    }

    /** Returns a copy of the farmer's password. */
    public char[] getPassword() {
        return Arrays.copyOf(password, password.length);
    }

    /** Updates the farmer's password. */
    public void setPassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        this.password = Arrays.copyOf(password, password.length);
    }

    /** Returns the type of crop the farmer grows. */
    public String getCropType() {
        return cropType;
    }

    /** Updates the type of crop the farmer grows. */
    public void setCropType(String cropType) {
        if (cropType == null || cropType.isBlank()) {
            throw new IllegalArgumentException("Crop type cannot be blank");
        }
        this.cropType = cropType;
    }

    /** Returns the farmer's net profit from last year. */
    public double getLastYearsNetProfit() {
        return lastYearsNetProfit;
    }

    /** Updates the farmer's net profit from last year. */
    public void setLastYearsNetProfit(double lastYearsNetProfit) {
        if (!Double.isFinite(lastYearsNetProfit)) {
            throw new IllegalArgumentException("Net profit must be a finite number");
        }
        this.lastYearsNetProfit = lastYearsNetProfit;
    }

    /** Clears the in-memory password characters. */
    public void clearPassword() {
        Arrays.fill(password, '\0');
    }
}
