package com.linguaai.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.linguaai.app.data.remote.audio.WiktionaryAudioRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WiktionaryAudioEntryPoint {
    fun wiktionaryAudioRepository(): WiktionaryAudioRepository
}

@Composable
internal fun rememberWiktionaryAudioRepository(): WiktionaryAudioRepository {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors
            .fromApplication(context, WiktionaryAudioEntryPoint::class.java)
            .wiktionaryAudioRepository()
    }
}
