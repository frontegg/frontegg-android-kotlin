package com.frontegg.android

import com.frontegg.android.exceptions.FronteggException
import org.junit.Test

class FronteggAppTest {

    @Test
    fun `getInstance should throw FronteggException_FRONTEGG_APP_MUST_BE_INITIALIZED`() {
        try {
            FronteggApp.getInstance()
        } catch (e: FronteggException) {
            assert(e.message == FronteggException.FRONTEGG_APP_MUST_BE_INITIALIZED)
        }
    }
}