-- Safe to rerun; preserves existing room names and active flags.
INSERT INTO rooms (room_number, name, is_active) VALUES
(1, '미팅룸 1실', TRUE),
(2, '미팅룸 2실', TRUE),
(3, '미팅룸 3실', TRUE),
(4, '미팅룸 4실', TRUE),
(5, '미팅룸 5실', TRUE),
(6, '미팅룸 6실', TRUE),
(7, '미팅룸 7실', TRUE),
(8, '미팅룸 8실', TRUE),
(9, '미팅룸 9실', TRUE),
(10, '미팅룸 10실', TRUE),
(11, '미팅룸 11실', TRUE),
(12, '미팅룸 12실', TRUE),
(13, '미팅룸 13실', TRUE)
AS incoming ON DUPLICATE KEY UPDATE room_number = incoming.room_number;
