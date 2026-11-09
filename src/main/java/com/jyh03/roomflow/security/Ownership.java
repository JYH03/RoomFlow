package com.jyh03.roomflow.security;

import org.springframework.security.access.AccessDeniedException;

public final class Ownership {
    private Ownership() { }

    public static void requireOwner(Long ownerId, Long currentUserId) {
        if (currentUserId == null || !currentUserId.equals(ownerId)) {
            throw new AccessDeniedException("본인의 예약 또는 대기열만 변경할 수 있습니다.");
        }
    }
}
