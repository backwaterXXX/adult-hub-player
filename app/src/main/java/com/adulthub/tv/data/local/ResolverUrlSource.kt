package com.adulthub.tv.data.local

/**
 * Origin of the effective Resolver address.
 *
 * The address can be configured explicitly, inherited from the Jellyfin server
 * address, or still empty. Showing the source tells the user which service the
 * app actually talks to.
 */
enum class ResolverUrlSource {
    /** The address was entered in the settings. */
    CONFIGURED,

    /** The address is inherited from the Jellyfin server address. */
    SERVER_FALLBACK,

    /** No address is known yet. */
    DEFAULT,
}
