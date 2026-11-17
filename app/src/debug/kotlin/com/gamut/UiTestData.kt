package com.gamut

import com.gamut.core.database.DownloadIndex
import com.gamut.feature.library.LibraryRepository
import com.gamut.podcasts.PodcastStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Compiled into the debug app so its generated component can expose instrumentation fixtures. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UiTestData {
    fun testLibraryRepository(): LibraryRepository
    fun testDownloadIndex(): DownloadIndex
    fun testPodcastStore(): PodcastStore
}
