package com.hsib.sib.core.social

/**
 * Hard limits for shared deadlines and chat. The server rules enforce the same numbers, so a modified app cannot get past
 * them; the interface uses them too, so an honest app never meets them by surprise.
 */
object SocialLimits {
    const val MEMBERS_PER_ROOM = 20
    const val ROOMS_PER_PERSON = 50
    const val MESSAGE_CHARS = 2000
    const val TITLE_CHARS = 200
    const val NOTES_CHARS = 4000
    const val DISPLAY_NAME_CHARS = 40
    const val REPORT_DETAIL_CHARS = 1000
    const val REPORT_QUOTE_CHARS = 2000
    const val INVITE_DAYS = 7L
    const val MESSAGE_GAP_MS = 1000L

    /** Largest plaintext that is ever sealed. Notes of 4,000 characters are at most 12,000 bytes once encoded. */
    const val MAX_SEALED_BYTES = 24_000

    /** Largest sealed text a server document may hold. Bigger values are refused before they are decoded. */
    const val MAX_ENVELOPE_CHARS = 40_000

    /** How many messages one fetch returns, so opening a long conversation never loads everything at once. */
    const val FETCH_PAGE = 200

    /** Bumped when the community rules change, so people are asked to accept them again. */
    const val TERMS_VERSION = 2
}
