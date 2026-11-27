package com.ivrtop.dialer.core.common

import com.google.common.truth.Truth.assertThat
import com.ivrtop.dialer.core.common.log.Redactor
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.common.phone.T9
import org.junit.Test

class PhoneNumbersTest {

    @Test fun `extensions and feature codes are never normalized`() {
        assertThat(PhoneNumbers.normalize("201")).isNull()
        assertThat(PhoneNumbers.normalize("*97")).isNull()
        assertThat(PhoneNumbers.normalize("#21#")).isNull()
        assertThat(PhoneNumbers.toDialString("*72 0501234567").target).isEqualTo("*720501234567")
    }

    @Test fun `israeli mobile normalizes to e164`() {
        assertThat(PhoneNumbers.normalize("050-123-4567")).isEqualTo("+972501234567")
        assertThat(PhoneNumbers.normalize("+972 50 123 4567")).isEqualTo("+972501234567")
    }

    @Test fun `bidi control characters are stripped`() {
        assertThat(PhoneNumbers.stripFormatting("‎050‑")).isEqualTo("050")
    }

    @Test fun `post dial digits are split`() {
        val d = PhoneNumbers.toDialString("035551234,,201")
        assertThat(d.target).isEqualTo("035551234")
        assertThat(d.postDial).isEqualTo(",,201")
    }

    @Test fun `sip uri kept as is`() {
        assertThat(PhoneNumbers.toDialString("alice@sip.ivr.top").target).isEqualTo("alice@sip.ivr.top")
        assertThat(PhoneNumbers.userFromSipUri("\"Bob\" <sip:201@sip.ivr.top;transport=tls>")).isEqualTo("201")
        assertThat(PhoneNumbers.displayNameFromSipUri("\"Bob\" <sip:201@sip.ivr.top>")).isEqualTo("Bob")
    }

    @Test fun `match key ignores country prefix`() {
        assertThat(PhoneNumbers.sameNumber("0501234567", "+972501234567")).isTrue()
    }

    @Test fun `t9 matches hebrew and latin`() {
        assertThat(T9.score("Moshe Cohen", "66")).isAtLeast(0)
        assertThat(T9.score("משה כהן", "4")).isAtLeast(0) // מ -> 4
        assertThat(T9.score("Dana", "999")).isEqualTo(-1)
    }

    @Test fun `redactor hides secrets`() {
        val raw = "Authorization: Digest username=\"201\", response=\"abc\"\n{\"password\":\"s3cr3t\"} https://x/setup?token=AbC123"
        val out = Redactor.redact(raw)
        assertThat(out).doesNotContain("s3cr3t")
        assertThat(out).doesNotContain("AbC123")
        assertThat(out).doesNotContain("response=")
    }
}
