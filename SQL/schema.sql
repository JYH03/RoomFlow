USE roomflow;

CREATE TABLE users(
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE rooms(
    id BIGINT AUTO_INCREMENT PRIMARY KEY
    room_number INT NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    createed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reservations(
    id BIGINT AUTO_INCREMENT PRIMARY KEY
    user_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL,
    checked_in_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_reservations_user
        Foreign Key (user_id) REFERENCES users(id),
    CONSTRAINT fk_reservaions_room
        Foreign Key (room_id) REFERENCES rooms(id),
    
);

CREATE TABLE waiting_queues (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    queue_order INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_waiting_queues_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_waiting_queues_room
        FOREIGN KEY (room_id) REFERENCES rooms(id),

    INDEX idx_waiting_queues_user_room_start_at
    (user_id, room_id, start_at),
    CONSTRAINT uk_waiting_queue_order
        UNIQUE (room_id, start_at, queue_order)
);

CREATE TABLE reservation_offers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    waiting_queue_id BIGINT NOT NULL,
    expires_at DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL,
    responded_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_reservation_offers_waiting_queue
        FOREIGN KEY (waiting_queue_id) REFERENCES waiting_queues(id)
);