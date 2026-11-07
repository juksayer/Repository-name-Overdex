package com.example.overdex.model.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualFramePublicationTrackerTest {
    @Test fun `accepted frames begin at one and expose the exact emitted metadata`() {
        val tracker = VisualFramePublicationTracker()
        val stream = tracker.startStream()
        assertNull(tracker.latest())
        val emitted = mutableListOf<VisualFramePublication>()

        assertTrue(tracker.publish(stream, 100L) { emitted.add(it) })
        assertSame(emitted[0], tracker.latest())
        assertEquals(VisualFramePublication(stream, 1L, 100L), tracker.latest())
        assertTrue(tracker.publish(stream, 150L) { emitted.add(it) })
        assertSame(emitted[1], tracker.latest())
        assertEquals(VisualFramePublication(stream, 2L, 150L), tracker.latest())
    }

    @Test fun `a rejected first emission does not advertise progress or consume a number`() {
        val tracker = VisualFramePublicationTracker()
        val stream = tracker.startStream()
        assertFalse(tracker.publish(stream, 100L) { false })
        assertNull(tracker.latest())
        assertTrue(tracker.publish(stream, 150L) { true })
        assertEquals(VisualFramePublication(stream, 1L, 150L), tracker.latest())
    }

    @Test fun `a rejected later emission leaves the last accepted snapshot intact`() {
        val tracker = VisualFramePublicationTracker()
        val stream = tracker.startStream()
        tracker.publish(stream, 100L) { true }
        val accepted = tracker.latest()
        assertFalse(tracker.publish(stream, 150L) { false })
        assertSame(accepted, tracker.latest())
        tracker.publish(stream, 200L) { true }
        assertEquals(VisualFramePublication(stream, 2L, 200L), tracker.latest())
    }

    @Test fun `restart has a new epoch and old callbacks cannot publish into it`() {
        val tracker = VisualFramePublicationTracker()
        val oldStream = tracker.startStream()
        tracker.publish(oldStream, 100L) { true }
        val newStream = tracker.startStream()
        assertNotEquals(oldStream, newStream)
        assertNull(tracker.latest())
        var called = false
        assertFalse(tracker.publish(oldStream, 150L) { called = true; true })
        assertFalse(called)
        assertNull(tracker.latest())
        assertTrue(tracker.publish(newStream, 200L) { true })
        assertEquals(VisualFramePublication(newStream, 1L, 200L), tracker.latest())
    }

    @Test fun `stop clears progress and late callbacks cannot resurrect it`() {
        val tracker = VisualFramePublicationTracker()
        val stream = tracker.startStream()
        tracker.publish(stream, 100L) { true }
        tracker.stopStream(stream)
        assertNull(tracker.latest())
        var called = false
        assertFalse(tracker.publish(stream, 200L) { called = true; true })
        assertFalse(called)
        assertNull(tracker.latest())
    }

    @Test fun `teardown of an older stream cannot clear a newer stream`() {
        val tracker = VisualFramePublicationTracker()
        val oldStream = tracker.startStream()
        val newStream = tracker.startStream()
        tracker.publish(newStream, 200L) { true }
        tracker.stopStream(oldStream)
        assertEquals(VisualFramePublication(newStream, 1L, 200L), tracker.latest())
    }

    @Test fun `throwing emitter cannot create a phantom accepted publication`() {
        val tracker = VisualFramePublicationTracker()
        val stream = tracker.startStream()
        val failure = IllegalStateException("fixture")
        try {
            tracker.publish(stream, 100L) { throw failure }
            throw AssertionError("Expected emitter failure")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
        assertNull(tracker.latest())
        tracker.publish(stream, 200L) { true }
        assertEquals(VisualFramePublication(stream, 1L, 200L), tracker.latest())
    }

    @Test fun `inline emission callback stopping the stream cannot resurrect progress`() {
        val tracker = VisualFramePublicationTracker()
        val stream = tracker.startStream()
        assertTrue(tracker.publish(stream, 100L) {
            tracker.stopStream(stream)
            true
        })
        assertNull(tracker.latest())
    }
}
