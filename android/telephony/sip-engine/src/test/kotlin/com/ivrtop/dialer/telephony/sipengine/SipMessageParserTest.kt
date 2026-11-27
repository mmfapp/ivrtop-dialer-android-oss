package com.ivrtop.dialer.telephony.sipengine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SipMessageParserTest {
    private val invite = listOf(
        "INVITE sip:201@10.0.0.5:5061;transport=tls SIP/2.0",
        "Via: SIP/2.0/TLS 1.2.3.4:5061;branch=z9hG4bK-1",
        "From: \"Dana Levi\" <sip:0501234567@sip.ivr.top>;tag=abc",
        "To: <sip:201@sip.ivr.top>",
        "i: 7f3a@pbx",
        "P-Asserted-Identity: \"Dana L\" <sip:0501234567@sip.ivr.top>",
        "X-IVRTOP-Call-ID: 6d7e3c1e-1111",
        "Content-Length: 0",
        "",
        "",
    ).joinToString("\r\n")

    @Test fun `parses headers including compact form`() {
        val h = SipMessageParser.headers(invite)
        assertThat(h["call-id"]).isEqualTo("7f3a@pbx")
        assertThat(h["x-ivrtop-call-id"]).isEqualTo("6d7e3c1e-1111")
    }

    @Test fun `prefers p-asserted-identity`() {
        val h = SipMessageParser.headers(invite)
        assertThat(SipMessageParser.remoteNumber("\"x\" <sip:anonymous@x>", h)).isEqualTo("0501234567")
        assertThat(SipMessageParser.remoteName("<sip:anonymous@x>", h)).isEqualTo("Dana L")
    }
}
