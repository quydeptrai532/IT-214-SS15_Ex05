package com.example.table.dto;

public record TableInfoResponse(String tableNumber, String status, String message, String heldBy, long heldSeconds) {
}
