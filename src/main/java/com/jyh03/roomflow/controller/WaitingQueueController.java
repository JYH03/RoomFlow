package com.jyh03.roomflow.controller;

import com.jyh03.roomflow.dto.waitingqueue.CreateWaitingQueueRequest;
import com.jyh03.roomflow.dto.waitingqueue.WaitingQueueResponse;
import com.jyh03.roomflow.service.WaitingQueueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@RestController
@RequestMapping("/api/waiting-queues")
public class WaitingQueueController {

    private final WaitingQueueService waitingQueueService;

    public WaitingQueueController(WaitingQueueService waitingQueueService) {
        this.waitingQueueService = waitingQueueService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WaitingQueueResponse joinWaitingQueue(
            @Valid @RequestBody CreateWaitingQueueRequest request) {
        return waitingQueueService.joinWaitingQueue(request);
    }

    @GetMapping
    public List<WaitingQueueResponse> getMyWaitingQueues(
            @RequestParam("userId") Long userId) {
        return waitingQueueService.getMyWaitingQueues(userId);
    }

    @PostMapping("/{waitingQueueId}/cancel")
    public WaitingQueueResponse cancelWaitingQueue(
            @PathVariable("waitingQueueId") Long waitingQueueId) {
        return waitingQueueService.cancelWaitingQueue(waitingQueueId);
    }
}