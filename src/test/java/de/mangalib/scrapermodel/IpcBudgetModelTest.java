package de.mangalib.scrapermodel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static de.mangalib.scrapermodel.SupervisorModel.Operation.*;
import static org.junit.jupiter.api.Assertions.*;

/** Kleine synthetische Bytezahlen statt realer Pipes oder großer Puffer. */
class IpcBudgetModelTest {
    private final SupervisorModel supervisor = new SupervisorModel(() -> 100,
            new SupervisorModel.Budgets(10, 100, 30, 5));
    private final SupervisorModel.Ticket ticket = admitted(supervisor);
    private final IpcBudgetModel ipc = new IpcBudgetModel(
            new IpcBudgetModel.Limits(8, 32, 16, 4, 10, 3), ticket);

    @Test
    void boundedChunksAtAllLimitsCanFinishWithoutAllocatingPayloads() {
        ipc.request(8);
        ipc.requestChunk(3);
        ipc.requestChunk(5);
        ipc.result(1, ticket, VOLUME, "SUCCESS", 32, 10, 3);
        ipc.resultChunk(12);
        ipc.resultChunk(20);
        ipc.stderr(10);
        ipc.stderr(6);
        assertEquals(4, ipc.retainedStderrBytes());
        ipc.end();
        assertTrue(ipc.finished());
        // Ein vollständiger IPC-Rahmen beweist kein Prozess-/Ressourcenende.
        assertFalse(supervisor.barrierConfirmed());
        assertEquals(0, supervisor.releases());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 9, Long.MAX_VALUE})
    void invalidRequestLengthFailsBeforeAnyBufferAllocation(long bytes) {
        assertThrows(IllegalStateException.class, () -> ipc.request(bytes));
        assertTrue(ipc.failed());
        assertThrows(IllegalStateException.class, () -> ipc.request(1));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 33, Long.MAX_VALUE})
    void invalidResultLengthFailsBeforeAnyBufferAllocation(long bytes) {
        request();
        assertThrows(IllegalStateException.class,
                () -> ipc.result(1, ticket, VOLUME, "SUCCESS", bytes, 1, 1));
        assertTrue(ipc.failed());
    }

    @Test
    void cumulativeStderrBudgetDoesNotConfuseRetainedTailWithTotalOutput() {
        ipc.stderr(16);
        assertEquals(4, ipc.retainedStderrBytes());
        assertThrows(IllegalStateException.class, () -> ipc.stderr(1));
        assertTrue(ipc.failed());
        assertFalse(ipc.finished());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 17, Long.MAX_VALUE})
    void stderrRejectsInvalidChunksAndOverflow(long bytes) {
        assertThrows(IllegalStateException.class, () -> ipc.stderr(bytes));
    }

    @Test
    void requestChunkCannotExceedDeclaredLength() {
        ipc.request(2);
        ipc.requestChunk(1);
        assertThrows(IllegalStateException.class, () -> ipc.requestChunk(2));
    }

    @Test
    void resultChunkCannotExceedDeclaredLength() {
        result();
        assertThrows(IllegalStateException.class, () -> ipc.resultChunk(3));
    }

    @Test
    void truncatedResultIsNotAnAcceptedEnd() {
        result();
        ipc.resultChunk(1);
        assertThrows(IllegalStateException.class, ipc::end);
        assertFalse(ipc.finished());
    }

    @Test
    void truncatedRequestCannotStartResult() {
        ipc.request(2);
        ipc.requestChunk(1);
        assertThrows(IllegalStateException.class,
                () -> ipc.result(1, ticket, VOLUME, "SUCCESS", 2, 1, 1));
    }

    @Test
    void requestCannotBeRepeated() {
        request();
        assertThrows(IllegalStateException.class, () -> ipc.request(2));
    }

    @Test
    void extraResultFrameIsRejected() {
        result();
        ipc.resultChunk(2);
        assertThrows(IllegalStateException.class,
                () -> ipc.result(1, ticket, VOLUME, "SUCCESS", 2, 1, 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 2, Integer.MAX_VALUE})
    void wrongProtocolVersionIsRejected(int version) {
        request();
        assertThrows(IllegalStateException.class,
                () -> ipc.result(version, ticket, VOLUME, "SUCCESS", 2, 1, 1));
    }

    @Test
    void wrongTicketIsRejectedEvenWithSameGenerationAndOperation() {
        request();
        var other = admitted(new SupervisorModel(() -> 100, new SupervisorModel.Budgets(10, 100, 30, 5)));
        assertEquals(ticket.generation(), other.generation());
        assertThrows(IllegalStateException.class,
                () -> ipc.result(1, other, VOLUME, "SUCCESS", 2, 1, 1));
    }

    @Test
    void wrongOperationIsRejected() {
        request();
        assertThrows(IllegalStateException.class,
                () -> ipc.result(1, ticket, SERIES, "SUCCESS", 2, 1, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "PARTIAL", "success", "java.lang.RuntimeException"})
    void arbitraryStatusIsRejected(String status) {
        request();
        assertThrows(IllegalStateException.class,
                () -> ipc.result(1, ticket, VOLUME, status, 2, 1, 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 11, Integer.MAX_VALUE})
    void entryCountIsBounded(int entries) {
        request();
        assertThrows(IllegalStateException.class,
                () -> ipc.result(1, ticket, VOLUME, "SUCCESS", 2, entries, 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 4, Integer.MAX_VALUE})
    void nestingDepthIsBounded(int depth) {
        request();
        assertThrows(IllegalStateException.class,
                () -> ipc.result(1, ticket, VOLUME, "SUCCESS", 2, 1, depth));
    }

    @Test
    void controlledErrorCanCompleteFrameButIsNotAScrapeSuccessOrRetry() {
        request();
        ipc.result(1, ticket, VOLUME, "ERROR", 2, 0, 1);
        ipc.resultChunk(2);
        ipc.end();
        assertTrue(ipc.finished());
        assertEquals(1, supervisor.startRequests());
        assertEquals(0, supervisor.releases());
    }

    @Test
    void bytesAfterEndAreNotSilentlyAccepted() {
        result();
        ipc.resultChunk(2);
        ipc.end();
        assertThrows(IllegalStateException.class, () -> ipc.stderr(1));
        assertTrue(ipc.failed());
        assertFalse(ipc.finished());
    }

    private void request() {
        ipc.request(2);
        ipc.requestChunk(2);
    }

    private void result() {
        request();
        ipc.result(1, ticket, VOLUME, "SUCCESS", 2, 1, 1);
    }

    private static SupervisorModel.Ticket admitted(SupervisorModel model) {
        assertTrue(model.initialize(SupervisorModel.Proof.CONFIRMED));
        return model.admit(VOLUME).orElseThrow();
    }
}
