package it.eng.dataplane.core.service;

import it.eng.dataplane.core.model.DataFlowCheckpoint;
import it.eng.dataplane.core.repository.DataFlowCheckpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for managing data flow checkpoints.
 *
 * <p>Checkpoints record the resumable state of in-progress multipart transfers so that
 * the Data Plane can recover interrupted uploads after a restart.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataFlowCheckpointService {

    private final DataFlowCheckpointRepository repository;

    /**
     * Finds the checkpoint for the given transfer process ID.
     *
     * @param processId the transfer process ID
     * @return optional checkpoint, empty if none recorded
     */
    public Optional<DataFlowCheckpoint> findByProcessId(String processId) {
        return repository.findById(processId);
    }

    /**
     * Returns the checkpoint for the given transfer process ID, throwing if absent.
     *
     * @param processId the transfer process ID
     * @return the checkpoint
     * @throws IllegalStateException if no checkpoint exists for the given process ID
     */
    public DataFlowCheckpoint findRequiredByProcessId(String processId) {
        return repository.findById(processId)
                .orElseThrow(() -> new IllegalStateException(
                        "No checkpoint found for processId: " + processId));
    }

    /**
     * Returns {@code true} if a resumable checkpoint exists for the given process ID.
     *
     * @param processId the transfer process ID
     * @return {@code true} if a checkpoint record exists; {@code false} otherwise
     */
    public boolean hasResumableCheckpoint(String processId) {
        return repository.findById(processId).isPresent();
    }

    /**
     * Persists the given checkpoint.
     *
     * @param checkpoint the checkpoint to save
     * @return the saved checkpoint
     */
    public DataFlowCheckpoint save(DataFlowCheckpoint checkpoint) {
        return repository.save(checkpoint);
    }

    /**
     * Removes the checkpoint for the given transfer process ID.
     * No-op if no checkpoint exists.
     *
     * @param processId the transfer process ID whose checkpoint should be removed
     */
    public void deleteByProcessId(String processId) {
        repository.deleteById(processId);
    }
}
