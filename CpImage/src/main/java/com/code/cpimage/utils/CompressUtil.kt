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
                Tools.cmd(
                    "guetzli",
                    "${imgFile.path} ${imgFile.path}"
                )
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