package com.jyh03.roomflow.global.error;

public class BookingConflictException extends IllegalArgumentException {
    public BookingConflictException(String message) { super(message); }
}
