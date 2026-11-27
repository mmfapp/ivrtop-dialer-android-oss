package com.ivrtop.dialer.telephony.sip

import com.google.common.truth.Truth.assertThat
import com.ivrtop.dialer.core.models.CallEndReason
import com.ivrtop.dialer.core.models.CallPhase
import com.ivrtop.dialer.telephony.sip.CallStateMachine.Event
import org.junit.Before
import org.junit.Test

class CallStateMachineTest {
    private var clock = 1_000L
    private val ignored = mutableListOf<String>()
    private lateinit var sm: CallStateMachine

    @Before fun setUp() {
        sm = CallStateMachine(now = { clock }, onIgnored = { ignored += it })
    }

    @Test fun `outgoing happy path`() {
        sm.dispatch(Event.OutgoingRequested("c1", "a1", "201", null, "sip:201@sip.ivr.top"))
        sm.dispatch(Event.OutgoingSent("c1"))
        sm.dispatch(Event.RemoteRinging("c1", earlyMedia = false))
        assertThat(sm["c1"]!!.phase).isEqualTo(CallPhase.RINGING_REMOTE)
        clock = 5_000
        sm.dispatch(Event.Connected("c1"))
        assertThat(sm["c1"]!!.phase).isEqualTo(CallPhase.ACTIVE)
        assertThat(sm["c1"]!!.connectedAtMillis).isEqualTo(5_000)
        sm.dispatch(Event.Ended("c1", CallEndReason.LOCAL_HANGUP, 200))
        assertThat(sm["c1"]!!.phase).isEqualTo(CallPhase.ENDED)
    }

    @Test fun `push then invite upgrades same call`() {
        sm.dispatch(Event.IncomingPush("p1", "a1", "0501234567", "Dana", pushCallId = "X1"))
        assertThat(sm["p1"]!!.phase).isEqualTo(CallPhase.INCOMING_PENDING)
        sm.dispatch(Event.IncomingInvite("p1", "a1", "0501234567", null, "sip:0501234567@sip.ivr.top", "X1"))
        assertThat(sm["p1"]!!.phase).isEqualTo(CallPhase.RINGING_LOCAL)
        assertThat(sm["p1"]!!.remoteDisplayName).isEqualTo("Dana")
    }

    @Test fun `answer tapped before invite keeps answering`() {
        sm.dispatch(Event.IncomingPush("p1", "a1", "050", null, "X1"))
        sm.dispatch(Event.AnswerRequested("p1"))
        sm.dispatch(Event.IncomingInvite("p1", "a1", "050", null, "sip:050@x", "X1"))
        assertThat(sm["p1"]!!.phase).isEqualTo(CallPhase.ANSWERING)
        sm.dispatch(Event.Connected("p1"))
        assertThat(sm["p1"]!!.phase).isEqualTo(CallPhase.ACTIVE)
    }

    @Test fun `duplicate push is ignored`() {
        sm.dispatch(Event.IncomingPush("p1", "a1", "050", null, "X1"))
        assertThat(sm.dispatch(Event.IncomingPush("p1", "a1", "050", null, "X1"))).isNull()
        assertThat(ignored).isNotEmpty()
    }

    @Test fun `ended is terminal`() {
        sm.dispatch(Event.OutgoingRequested("c1", "a1", "201", null, "sip:201@x"))
        sm.dispatch(Event.Ended("c1", CallEndReason.BUSY, 486))
        assertThat(sm.dispatch(Event.Connected("c1"))).isNull()
        assertThat(sm["c1"]!!.endReason).isEqualTo(CallEndReason.BUSY)
    }

    @Test fun `hold and remote hold`() {
        sm.dispatch(Event.OutgoingRequested("c1", "a1", "201", null, "sip:201@x"))
        sm.dispatch(Event.Connected("c1"))
        sm.dispatch(Event.HoldChanged("c1", localHold = true, remoteHold = false))
        assertThat(sm["c1"]!!.phase).isEqualTo(CallPhase.HOLDING)
        sm.dispatch(Event.HoldChanged("c1", localHold = false, remoteHold = true))
        assertThat(sm["c1"]!!.phase).isEqualTo(CallPhase.REMOTE_HELD)
        sm.dispatch(Event.HoldChanged("c1", localHold = false, remoteHold = false))
        assertThat(sm["c1"]!!.phase).isEqualTo(CallPhase.ACTIVE)
    }

    @Test fun `removed deletes the call`() {
        sm.dispatch(Event.OutgoingRequested("c1", "a1", "201", null, "sip:201@x"))
        sm.dispatch(Event.Ended("c1", CallEndReason.CANCELLED, null))
        sm.dispatch(Event.Removed("c1"))
        assertThat(sm["c1"]).isNull()
    }

    @Test fun `status mapper treats final 401 as auth failure but 486 as busy`() {
        assertThat(SipStatusMapper.endReason(486, localHangup = false, wasConnected = false)).isEqualTo(CallEndReason.BUSY)
        assertThat(SipStatusMapper.registrationError(401, transportError = false).name).isEqualTo("AUTH_FAILED")
        assertThat(SipStatusMapper.registrationError(503, transportError = true).name).isEqualTo("SERVER_UNREACHABLE")
    }
}
