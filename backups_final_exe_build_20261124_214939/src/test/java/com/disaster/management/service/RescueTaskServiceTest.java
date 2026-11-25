package com.disaster.management.service;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.disaster.management.entity.RescueTask;
import com.disaster.management.repository.RescueTaskRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RescueTaskServiceTest {

    @Mock
    private RescueTaskRepository taskRepository;

    @InjectMocks
    private RescueTaskService taskService;

    private RescueTask task;

    @BeforeEach
    void setUp() {
        task = new RescueTask();
        task.setTaskId(1L);
        task.setTitle("Evacuate Riverbank");
        task.setDescription("Evacuate residents near Noyyal river");
        task.setZone("Coimbatore");
        task.setPriority("High");
        task.setStatus("Pending");
    }

    @Test
    @DisplayName("Create valid task succeeds")
    void testCreateTaskSuccess() {
        when(taskRepository.save(any(RescueTask.class))).thenReturn(task);

        RescueTask created = taskService.createTask(task);

        assertNotNull(created);
        assertEquals("Evacuate Riverbank", created.getTitle());
        assertEquals("High", created.getPriority());
        assertEquals("Pending", created.getStatus());
        verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("Create task with invalid priority throws exception")
    void testCreateTaskInvalidPriority() {
        task.setPriority("Urgent"); // Invalid priority value
        assertThrows(IllegalArgumentException.class, () -> taskService.createTask(task));
    }

    @Test
    @DisplayName("Assign task to responder updates assignment fields")
    void testAssignTask() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(RescueTask.class))).thenAnswer(i -> i.getArgument(0));

        RescueTask assigned = taskService.assignTask(1L, "responder@disaster.gov.in", "Emergency Responder");

        assertNotNull(assigned);
        assertEquals("responder@disaster.gov.in", assigned.getAssignedTo());
        assertEquals("Emergency Responder", assigned.getAssignedResponderName());
        verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("Update task status to In Progress")
    void testUpdateTaskStatus() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(RescueTask.class))).thenAnswer(i -> i.getArgument(0));

        RescueTask updated = taskService.updateTaskStatus(1L, "In Progress", "Units deployed");

        assertNotNull(updated);
        assertEquals("In Progress", updated.getStatus());
        assertTrue(updated.getNotes().contains("Units deployed"));
        verify(taskRepository).save(task);
    }
}
