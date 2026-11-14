package com.ricky.thyme.feature.services;

public record ServiceSnapshot(
        String unit,
        String load,
        String active,
        String sub,
        String description) {

}
