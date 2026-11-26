package com.example.data

enum class HomeSortField {
    DATE,
    TITLE,
    ARTIST,
    ALBUM,
    DURATION;

    fun defaultAscending(): Boolean = when (this) {
        DATE, DURATION -> false
        TITLE, ARTIST, ALBUM -> true
    }
}

enum class HomeFilterType {
    ALL,
    FAVORITES,
    DEVICE,
    RECENT,
    LONG_TRACKS,
}

object HomeTrackSorter {
    private const val LONG_TRACK_THRESHOLD_MS = 180_000L

    fun filterAndSort(
        tracks: List<Track>,
        sortField: HomeSortField,
        sortAscending: Boolean,
        filterType: HomeFilterType,
        favoriteIds: Set<String> = emptySet(),
        recentTrackIds: Set<String> = emptySet(),
    ): List<Track> {
        val filtered = when (filterType) {
            HomeFilterType.ALL -> tracks
            HomeFilterType.FAVORITES -> tracks.filter { it.id in favoriteIds }
            HomeFilterType.DEVICE -> tracks.filter { !it.isBuiltIn }
            HomeFilterType.RECENT -> tracks.filter { it.id in recentTrackIds }
            HomeFilterType.LONG_TRACKS -> tracks.filter { it.durationMs >= LONG_TRACK_THRESHOLD_MS }
        }

        val comparator: Comparator<Track> = when (sortField) {
            HomeSortField.DATE -> {
                if (sortAscending) {
                    compareBy<Track> { it.dateAdded }
                        .thenBy { it.title.trim().lowercase() }
                        .thenBy { it.id }
                } else {
                    compareByDescending<Track> { it.dateAdded }
                        .thenBy { it.title.trim().lowercase() }
                        .thenBy { it.id }
                }
            }

            HomeSortField.TITLE -> {
                if (sortAscending) {
                    compareBy<Track> { it.title.trim().lowercase() }
                        .thenBy { it.artist.trim().lowercase() }
                        .thenBy { it.id }
                } else {
                    compareByDescending<Track> { it.title.trim().lowercase() }
                        .thenBy { it.artist.trim().lowercase() }
                        .thenBy { it.id }
                }
            }

            HomeSortField.ARTIST -> {
                if (sortAscending) {
                    compareBy<Track> { it.artist.trim().lowercase() }
                        .thenBy { it.title.trim().lowercase() }
                        .thenBy { it.id }
                } else {
                    compareByDescending<Track> { it.artist.trim().lowercase() }
                        .thenBy { it.title.trim().lowercase() }
                        .thenBy { it.id }
                }
            }

            HomeSortField.ALBUM -> {
                if (sortAscending) {
                    compareBy<Track> { it.album.trim().lowercase() }
                        .thenBy { it.title.trim().lowercase() }
                        .thenBy { it.id }
                } else {
                    compareByDescending<Track> { it.album.trim().lowercase() }
                        .thenBy { it.title.trim().lowercase() }
                        .thenBy { it.id }
                }
            }

            HomeSortField.DURATION -> {
                if (sortAscending) {
                    compareBy<Track> { it.durationMs }
                        .thenBy { it.title.trim().lowercase() }
                        .thenBy { it.id }
                } else {
                    compareByDescending<Track> { it.durationMs }
                        .thenBy { it.title.trim().lowercase() }
                        .thenBy { it.id }
                }
            }
        }

        return filtered.sortedWith(comparator)
    }
}
