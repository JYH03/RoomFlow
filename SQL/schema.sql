-- Fresh installation only. Select your database before running this file.
-- Existing databases: do not drop tables or rerun this as a migration.
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_number INT NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    checked_in_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_reservations_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_reservations_room FOREIGN KEY (room_id) REFERENCES rooms(id),
    INDEX idx_reservations_room_start_at (room_id, start_at, end_at, status),
    INDEX idx_reservations_user_start_at (user_id, start_at, end_at, status),
    INDEX idx_reservations_due (status, start_at, created_at, end_at),
    CONSTRAINT ck_reservation_time CHECK (end_at > start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE waiting_queues (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    queue_order INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_waiting_queues_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_waiting_queues_room FOREIGN KEY (room_id) REFERENCES rooms(id),
    INDEX idx_waiting_queues_user_room_start_at (user_id, room_id, start_at),
    CONSTRAINT uk_waiting_queue_order UNIQUE (room_id, start_at, queue_order),
    CONSTRAINT ck_waiting_time CHECK (end_at > start_at),
    CONSTRAINT ck_waiting_order CHECK (queue_order > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reservation_offers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    waiting_queue_id BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    responded_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_reservation_offers_waiting_queue FOREIGN KEY (waiting_queue_id) REFERENCES waiting_queues(id),
    INDEX idx_offers_expiry (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
