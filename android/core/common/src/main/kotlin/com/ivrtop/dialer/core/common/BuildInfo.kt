package com.ivrtop.dialer.core.common

/** Values injected by :app from BuildConfig so library modules stay build-type agnostic. */
data class BuildInfo(
    val versionName: String,
    val versionCode: Int,
    val debug: Boolean,
    /** Setup links: https://<appLinkHost><appLinkPath>?user=…&pass=… */
    val appLinkHost: String,
    val appLinkPath: String,
    val defaultSipDomain: String,
    /** SIP domains a setup link is allowed to configure (lower-case). */
    val linkAllowedDomains: Set<String>,
    val userAgent: String,
)
