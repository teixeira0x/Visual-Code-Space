/*
 * This file is part of Visual Code Space.
 *
 * Visual Code Space is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Visual Code Space is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Visual Code Space.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package com.teixeira.vcspace.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

/** Regex used to check if file name extension is not of a text file. */
val INVALID_TEXT_FILES_REGEX =
  Regex(
    ".*\\.(bin|ttf|png|jpe?g|bmp|mp4|mp3|m4a|iso|so|zip|rar|jar|dex|odex|vdex|7z|apk|apks|xapk)$"
  )

/**
 * Checks if the file with the given name is a valid text file, checking if the name extension is
 * not in the [INVALID_TEXT_FILES_REGEX] regex.
 *
 * @param filename The file name to check the extension.
 * @return If it is a valid text file.
 */
fun isValidTextFile(filename: String): Boolean {
  return !filename.matches(INVALID_TEXT_FILES_REGEX)
}

/**
 * Checks if storage permission has been granted.
 *
 * @return If permission has been granted.
 */
fun Context.isStoragePermissionGranted(): Boolean {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    Environment.isExternalStorageManager()
  } else {
    (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
      PackageManager.PERMISSION_GRANTED)
  }
}

/**
 * Get the previous path from the current path, for example: [/path1/path2] it will get the
 * [/path1].
 *
 * @param path The way to get the parent path.
 * @return The parent path.
 */
fun getParentDirPath(path: String): String {
  val index = path.lastIndexOf("/")
  return if (index >= 0) {
    path.substring(0, index)
  } else path
}