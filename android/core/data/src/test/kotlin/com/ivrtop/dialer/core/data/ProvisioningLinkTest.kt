package com.ivrtop.dialer.core.data

import com.google.common.truth.Truth.assertThat
import com.ivrtop.dialer.core.common.BuildInfo
import com.ivrtop.dialer.core.models.AppErrorCode
import com.ivrtop.dialer.core.models.SipTransport
import org.junit.Test

class ProvisioningLinkTest {
    private val info = BuildInfo(
        versionName = "1.0", versionCode = 1, debug = true,
        appLinkHost = "ivr.top", appLinkPath = "/dialer/android",
        defaultSipDomain = "sip.ivr.top", linkAllowedDomains = setOf("sip.ivr.top"),
        userAgent = "test",
    )

    private fun ok(raw: String) = (ProvisioningRepository.parse(raw, info) as SetupLinkResult.Ok).request.account
    private fun rejected(raw: String) = ProvisioningRepository.parse(raw, info) as SetupLinkResult.Rejected

    @Test fun `basic query link`() {
        val a = ok("https://ivr.top/dialer/android?user=201&pass=Secret1")
        assertThat(a.username).isEqualTo("201")
        assertThat(a.password).isEqualTo("Secret1")
        assertThat(a.domain).isEqualTo("sip.ivr.top")
        assertThat(a.transport).isNull() // auto-detect
        assertThat(a.displayName).isEqualTo("201")
    }

    @Test fun `fragment form and optional fields`() {
        val a = ok("https://ivr.top/dialer/android#user=201&pass=p%26ss&name=%D7%9E%D7%A9%D7%A8%D7%93&number=050-1234567&transport=UDP&port=5080")
        assertThat(a.password).isEqualTo("p&ss")
        assertThat(a.displayName).isEqualTo("משרד")
        assertThat(a.phoneNumber).isEqualTo("0501234567")
        assertThat(a.transport).isEqualTo(SipTransport.UDP)
        assertThat(a.port).isEqualTo(5080)
    }

    @Test fun `plus sign in password is kept literally`() {
        assertThat(ok("https://ivr.top/dialer/android?user=201&pass=a+b").password).isEqualTo("a+b")
    }

    @Test fun `trailing slash and host case are accepted`() {
        assertThat(ok("https://IVR.TOP/dialer/android/?user=201&pass=x").username).isEqualTo("201")
    }

    @Test fun `foreign sip domain is blocked`() {
        assertThat(rejected("https://ivr.top/dialer/android?user=201&pass=x&domain=evil.example").error)
            .isEqualTo(AppErrorCode.LINK_DOMAIN_NOT_ALLOWED)
        assertThat(ok("https://ivr.top/dialer/android?user=201&pass=x&domain=SIP.IVR.TOP").domain).isEqualTo("sip.ivr.top")
    }

    @Test fun `wrong host path scheme or missing fields are rejected`() {
        listOf(
            "http://ivr.top/dialer/android?user=201&pass=x",
            "https://evil.tel/dialer/android?user=201&pass=x",
            "https://ivr.top/app/other?user=201&pass=x",
            "https://ivr.top/dialer/android?user=201",
            "https://ivr.top/dialer/android?pass=x",
            "https://ivr.top/dialer/android?user=20%201&pass=x",
            "https://ivr.top/dialer/android?user=201&pass=x&transport=ws",
            "https://ivr.top/dialer/android?user=201&pass=x&port=99999",
            "not a url",
        ).forEach { assertThat(ProvisioningRepository.parse(it, info)).isInstanceOf(SetupLinkResult.Rejected::class.java) }
    }
}
