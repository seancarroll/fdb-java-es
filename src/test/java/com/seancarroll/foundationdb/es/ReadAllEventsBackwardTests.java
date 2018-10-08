package com.seancarroll.foundationdb.es;

import com.apple.foundationdb.Database;
import com.apple.foundationdb.FDB;
import com.apple.foundationdb.directory.DirectorySubspace;
import com.apple.foundationdb.tuple.Versionstamp;
import com.google.common.collect.ObjectArrays;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class ReadAllEventsBackwardTests extends TestFixture {

    @BeforeEach
    void clean() {
        FDB fdb = FDB.selectAPIVersion(520);
        TestHelpers.clean(fdb);
    }

    // TODO: Not sure this makes sense especially when we can read from start and end for the read event stream version which I think should have the same behavior
    // If I read from the start I would get that starting event...so why would it be different if I read from the end?
    // I should get the end event...no?
    // what about backward with the START position. that should behave in the same manner
    @Test
    void shouldReturnEmptyPageWhenAskedToReadFromStart() throws ExecutionException, InterruptedException {
        FDB fdb = FDB.selectAPIVersion(520);
        try (Database db = fdb.open()) {
            DirectorySubspace eventStoreSubspace = createEventStoreSubspace(db);
            EventStoreLayer es = new EventStoreLayer(db, eventStoreSubspace);

            NewStreamMessage[] messages = createNewStreamMessages(1, 2, 3, 4, 5);
            es.appendToStream("test-stream", ExpectedVersion.ANY, messages);

            ReadAllPage read = es.readAllBackwards(Position.START, 1);

            assertTrue(read.isEnd());
            assertEquals(0, read.getMessages().length);
        }
    }

    @Test
    void shouldReturnEventsInReversedOrderComparedToWritten() throws ExecutionException, InterruptedException {
        FDB fdb = FDB.selectAPIVersion(520);
        try (Database db = fdb.open()) {
            DirectorySubspace eventStoreSubspace = createEventStoreSubspace(db);
            EventStoreLayer es = new EventStoreLayer(db, eventStoreSubspace);

            NewStreamMessage[] messages = createNewStreamMessages(1, 2, 3, 4, 5);
            es.appendToStream("test-stream", ExpectedVersion.ANY, messages);

            ReadAllPage read = es.readAllBackwards(Position.END, messages.length);

            ArrayUtils.reverse(messages);
            TestHelpers.assertEventDataEqual(messages, read.getMessages());
        }
    }

    @Test
    void shouldBeAbleToReadAllOneByOneUntilEnd() throws ExecutionException, InterruptedException {
        FDB fdb = FDB.selectAPIVersion(520);
        try (Database db = fdb.open()) {
            DirectorySubspace eventStoreSubspace = createEventStoreSubspace(db);
            EventStoreLayer es = new EventStoreLayer(db, eventStoreSubspace);

            NewStreamMessage[] messages = createNewStreamMessages(1, 2, 3, 4, 5);
            es.appendToStream("test-stream", ExpectedVersion.ANY, messages);

            List<StreamMessage> all = new ArrayList<>();
            Versionstamp position = Position.END;
            ReadAllPage page;
            boolean atEnd = false;
            while (!atEnd) {
                page = es.readAllBackwards(position, 1);
                all.addAll(Arrays.asList(page.getMessages()));
                position = page.getNextPosition();
                atEnd = page.isEnd();
            }

            ArrayUtils.reverse(messages);
            StreamMessage[] messagesArray = new StreamMessage[all.size()];
            TestHelpers.assertEventDataEqual(messages, all.toArray(messagesArray));
        }
    }

    @Test
    void shouldBeAbleToPageViaReadNext() throws ExecutionException, InterruptedException {
        FDB fdb = FDB.selectAPIVersion(520);
        try (Database db = fdb.open()) {
            DirectorySubspace eventStoreSubspace = createEventStoreSubspace(db);
            EventStoreLayer es = new EventStoreLayer(db, eventStoreSubspace);

            NewStreamMessage[] messages = createNewStreamMessages(1, 2, 3, 4, 5);
            es.appendToStream("test-stream", ExpectedVersion.ANY, messages);

            List<StreamMessage> all = new ArrayList<>();
            Versionstamp position = Position.END;
            ReadAllPage page;

            // TODO: implement
            //TestHelpers.assertEventDataEqual(messages, new N);
        }
    }

    @Test
    void shouldBeAbleToReadEventsPageAtATime() throws ExecutionException, InterruptedException {
        FDB fdb = FDB.selectAPIVersion(520);
        try (Database db = fdb.open()) {
            DirectorySubspace eventStoreSubspace = createEventStoreSubspace(db);
            EventStoreLayer es = new EventStoreLayer(db, eventStoreSubspace);

            NewStreamMessage[] messages = createNewStreamMessages(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
            es.appendToStream("test-stream", ExpectedVersion.ANY, messages);

            List<StreamMessage> all = new ArrayList<>();
            Versionstamp position = Position.END;
            ReadAllPage page;
            boolean atEnd = false;
            while (!atEnd) {
                page = es.readAllBackwards(position, 5);
                all.addAll(Arrays.asList(page.getMessages()));
                position = page.getNextPosition();
                atEnd = page.isEnd();
            }

            ArrayUtils.reverse(messages);
            StreamMessage[] messagesArray = new StreamMessage[all.size()];
            TestHelpers.assertEventDataEqual(messages, all.toArray(messagesArray));
        }
    }

    @Test
    void shouldReturnPartialPageIfNotEnoughEvents() throws ExecutionException, InterruptedException {
        FDB fdb = FDB.selectAPIVersion(520);
        try (Database db = fdb.open()) {
            DirectorySubspace eventStoreSubspace = createEventStoreSubspace(db);
            EventStoreLayer es = new EventStoreLayer(db, eventStoreSubspace);

            NewStreamMessage[] messages = createNewStreamMessages(1, 2, 3, 4, 5);
            es.appendToStream("test-stream", ExpectedVersion.ANY, messages);

            ReadAllPage read = es.readAllBackwards(Position.END, 10);

            ArrayUtils.reverse(messages);
            assertTrue(read.getMessages().length < 10);
            TestHelpers.assertEventDataEqual(messages, read.getMessages());
        }
    }

    @Test
    void shouldThrowWhenMaxCountExceedsMaxReadCount() {
        FDB fdb = FDB.selectAPIVersion(520);
        try (Database db = fdb.open()) {
            DirectorySubspace eventStoreSubspace = createEventStoreSubspace(db);
            EventStoreLayer es = new EventStoreLayer(db, eventStoreSubspace);

            assertThrows(IllegalArgumentException.class, () -> es.readAllBackwards(Position.END, EventStoreLayer.MAX_READ_SIZE + 1));
        }
    }

    @Test
    void shouldReadFromMultipleStream() throws ExecutionException, InterruptedException {
        FDB fdb = FDB.selectAPIVersion(520);
        try (Database db = fdb.open()) {
            DirectorySubspace eventStoreSubspace = createEventStoreSubspace(db);
            EventStoreLayer es = new EventStoreLayer(db, eventStoreSubspace);

            NewStreamMessage[] messages = createNewStreamMessages(1, 2);
            es.appendToStream("test-stream", ExpectedVersion.ANY, messages);
            es.appendToStream("test-stream2", ExpectedVersion.ANY, messages);

            ReadAllPage read = es.readAllBackwards(Position.END, 4);

            assertEquals(4, read.getMessages().length);
            assertTrue(read.isEnd());
            NewStreamMessage[] combined = ObjectArrays.concat(messages, messages, NewStreamMessage.class);
            ArrayUtils.reverse(combined);
            TestHelpers.assertEventDataEqual(combined, read.getMessages());
        }
    }

    @Test
    void readAllBackwardNextPage() throws ExecutionException, InterruptedException {
        FDB fdb = FDB.selectAPIVersion(520);
        try (Database db = fdb.open()) {
            DirectorySubspace eventStoreSubspace = createEventStoreSubspace(db);
            EventStoreLayer es = new EventStoreLayer(db, eventStoreSubspace);

            NewStreamMessage[] messages = createNewStreamMessages(1, 2, 3, 4, 5);
            AppendResult appendResult = es.appendToStream("test-stream", ExpectedVersion.ANY, messages);

            // TODO: improve test
            // Does start make sense here? What does EventStore do?
            ReadAllPage backwardsPage = es.readAllBackwards(Position.END, 1);
            assertNotNull(backwardsPage);
            assertTrue(backwardsPage.getMessages()[0].getMessageId().toString().contains("5"));

            ReadAllPage nextPage = backwardsPage.readNext();
            assertNotNull(nextPage);
            assertTrue(nextPage.getMessages()[0].getMessageId().toString().contains("4"));
        }
    }

}
