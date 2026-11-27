package com.ivrtop.dialer.ui

/**
 * One-shot, in-memory hand-off of a setup link from MainActivity to the onboarding screen.
 * The link contains the SIP password, so it must never travel as a navigation argument
 * (those are written into saved instance state) nor be logged.
 */
object SetupLinkHolder {
    @Volatile private var link: String? = null

    fun put(value: String) { link = value }

    /** Returns the link once and clears it. */
    fun take(): String? = link.also { link = null }
}
