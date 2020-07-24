package com.code.cpimage.utils

import java.io.File

class CompressUtil {

    companion object {
        private const val TAG = "Compress"
        fun compressImg(imgFile: File) {
            if (!ImageUtil.isImage(imgFile)) {
                return
            }
            val oldSize = imgFile.length()
            val newSize: Long
            if (ImageUtil.isJPG(imgFile)) {
                val tempFilePath: String =
                    "${imgFile.path.substring(0, imgFile.path.lastIndexOf("."))}_temp" +
                            imgFile.path.substring(imgFile.path.lastIndexOf("."))
                Tools.cmd(
                    "guetzli",
                    "${imgFile.path} ${tempFilePath}"
                )
                val tempFile = File(tempFilePath)
                if (!tempFile.exists()) {
                    return
                }

                if (tempFile.length() >= imgFile.length()) {
                    tempFile.delete()
                    return
                } else {
                    val imgFileName = imgFile.path
                    if (imgFile.exists()) {
                        imgFile.delete()
                    }
                    tempFile.renameTo(File(imgFileName))
                }
            } else if (ImageUtil.isPNG(imgFile)) {
                Tools.cmd(
                    "pngquant",
                    "--skip-if-larger --speed 1 --nofs --strip ${imgFile.path} --ext=.png --force"
                )
            }
            newSize = File(imgFile.path).length()
            if (newSize != oldSize)
                LogUtil.log(
                    TAG,
                    imgFile.path,
                    oldSize.toString(),
                    newSize.toString()
                )
        }
    }

}