package com.jyh03.roomflow.repository;

import com.jyh03.roomflow.domain.room.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {
}