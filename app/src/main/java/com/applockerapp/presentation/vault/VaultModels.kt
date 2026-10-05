package com.applockerapp.presentation.vault

import android.net.Uri

data class VaultAlbum(
    val name: String,
    val count: Int,
    val thumbnailUri: Uri?,
    val bucketId: Long,
    val mediaType: Int  // MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE or _VIDEO
)

data class VaultMediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val duration: Long = 0L
)
