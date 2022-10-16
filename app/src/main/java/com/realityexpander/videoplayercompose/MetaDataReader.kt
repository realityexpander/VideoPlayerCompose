package com.realityexpander.videoplayercompose

import android.app.Application
import android.net.Uri
import android.provider.MediaStore

data class MetaData(
    val fileName: String
)

interface MetaDataReader {
    fun getMetaDataFromUri(contentUri: Uri): MetaData?
}

class MetaDataReaderImpl(
    private val app: Application
): MetaDataReader {

    override fun getMetaDataFromUri(contentUri: Uri): MetaData? {
        if(contentUri.scheme != "content") {
            return null
        }

        val fileName = app.contentResolver
            .query(  // sets up a SQL query to get the file name
                contentUri,
                arrayOf(MediaStore.Video.VideoColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )
            ?.use { cursor -> // cursor is a pointer to the first row of the result set

                // find the column index of the display name of the result set
                val index = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DISPLAY_NAME)

                // Go to the first row of the result set
                cursor.moveToFirst()

                // get the value of the display name column
                cursor.getString(index)
            }

        return fileName?.let { fullFileName ->
            MetaData(
                fileName = Uri.parse(fullFileName).lastPathSegment
                    ?: return null
            )
        }
    }
}