package com.verisonder.sonderweb

import android.app.Application

/**
 * The engine also runs pages in separate processes that start this class too, so nothing
 * heavy happens here: the browser starts with the first screen (see MainActivity).
 */
class SonderWebApp : Application()
