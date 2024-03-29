package com.code.cpimage

import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.api.BaseVariantImpl
import com.android.build.gradle.internal.pipeline.TransformManager
import com.code.cpimage.iface.IBigImage
import com.code.cpimage.utils.*
import com.code.cpimage.webp.WebpUtils
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

class CpImage : Transform(), Plugin<Project> {

    private lateinit var mcImageProject: Project
    private lateinit var mcImageConfig: Config
    private var oldCompressSize: Long = 0
    private var newCompressSize: Long = 0
    private val noSizeChangeResource = mutableListOf<String>()
    val bigImgList = ArrayList<String>()
    var oldReplaceSize = 0L
    var newReplaceSize = 0L
    val systemDefaultResPath =
        if (Tools.isMac()) ".gradle/caches/transforms" else ".gradle\\caches\\transforms"
    var isDebugTask = false
    var isContainAssembleTask = false

    override fun apply(project: Project) {
        System.out.println("ktplugin")
        mcImageProject = project
//        project.extensions.getByType()
        //check is library or application
        val hasAppPlugin = project.plugins.hasPlugin("com.android.application")
        val variants = if (hasAppPlugin) {
            (project.property("android") as AppExtension).applicationVariants
        } else {
            (project.property("android") as LibraryExtension).libraryVariants
        }

        //set config
        project.extensions.create("McImageConfig", Config::class.java)
        mcImageConfig = project.property("McImageConfig") as Config
        project.gradle.taskGraph.whenReady {
            it.allTasks.forEach { task ->
                val taskName = task.name
                if (taskName.contains("assemble") || taskName.contains("resguard") || taskName.contains(
                        "bundle"
                    )
                ) {
                    if (taskName.toLowerCase().endsWith("debug") &&
                        taskName.toLowerCase().contains("debug")
                    ) {
                        isDebugTask = true
                    }
                    isContainAssembleTask = true
                    return@forEach
                }
            }
        }

        project.afterEvaluate {
            variants.all { variant ->

                variant as BaseVariantImpl
                checkMcTools(project)

                val mergeResourcesTask = variant.mergeResourcesProvider.get()
                val mcPicTask = project.task("McImage${variant.name.capitalize()}")

                mcPicTask.doLast {

                    //debug enable
                    if (isDebugTask && !mcImageConfig.enableWhenDebug) {
                        LogUtil.log("Debug not run ^_^")
                        return@doLast
                    }

                    //assemble passed
                    if (!isContainAssembleTask) {
                        LogUtil.log("Don't contain assemble task, mcimage passed")
                        return@doLast
                    }

                    LogUtil.log("---- McImage Plugin Start ----")
                    LogUtil.log(mcImageConfig.toString())

                    val dir = variant.allRawAndroidResources.files

                    val cacheList = ArrayList<String>()

                    val imageFileList = ArrayList<File>()

                    for (channelDir: File in dir) {
                        traverseResDir(channelDir, imageFileList, cacheList, object :
                            IBigImage {
                            override fun onBigImage(file: File) {
                                bigImgList.add(file.absolutePath)
                            }
                        })
                    }
                    checkBigImage()

                    val start = System.currentTimeMillis()
                    mtDispatchOptimizeTask(imageFileList)
                    LogUtil.log(replaceSizeInfo())
                    LogUtil.log(compressSizeInfo())
                    LogUtil.log(totalOptimizeInfo())
                    LogUtil.log(compressNoSizeChangeInfo())
                    LogUtil.log("---- McImage Plugin End ----, Total Time(ms) : ${System.currentTimeMillis() - start}")
                }

                //chmod task
                val chmodTaskName = "chmod${variant.name.capitalize()}"
                val chmodTask = project.task(chmodTaskName)
                chmodTask.doLast {
                    //chmod if linux
                    if (Tools.isLinux()) {
                        Tools.chmod()
                    }
                }

                //inject task
                (project.tasks.findByName(chmodTask.name) as Task).dependsOn(
                    mergeResourcesTask.taskDependencies.getDependencies(
                        mergeResourcesTask
                    )
                )
                (project.tasks.findByName(mcPicTask.name) as Task).dependsOn(
                    project.tasks.findByName(
                        chmodTask.name
                    ) as Task
                )
                mergeResourcesTask.dependsOn(project.tasks.findByName(mcPicTask.name))
            }
        }
    }

    private fun traverseResDir(
        file: File,
        imageFileList: ArrayList<File>,
        cacheList: ArrayList<String>,
        iBigImage: IBigImage
    ) {
        if (cacheList.contains(file.absolutePath)) {
            return
        } else {
            cacheList.add(file.absolutePath)
        }
        if (file.isDirectory) {
            file.listFiles()?.forEach {
                if (it.isDirectory) {
                    traverseResDir(it, imageFileList, cacheList, iBigImage)
                } else {
                    filterImage(it, imageFileList, iBigImage)
                }
            }
        } else {
            filterImage(file, imageFileList, iBigImage)
        }
    }

    private fun removeSystemDefaultRes(file: File): Boolean {
        file.let {
            if (!file.exists()) {
                return true
            }
            if (file.isDirectory) {
                return false
            }
            if (file.absolutePath.contains(systemDefaultResPath)) {
                val old = file.length()
                val replace_file = File(FileUtil.getToolsDirPath() + "replace.png")
                replace_file.copyTo(file, true)
                LogUtil.log("[replace]:" + file.absolutePath + "    old:" + old + " new:" + file.length())
                oldReplaceSize += old
                newReplaceSize += file.length()
                return true
            }
            if (file.name.startsWith("ic_launcher")) {
                return true
            }
        }
        return false
    }

    private fun filterImage(file: File, imageFileList: ArrayList<File>, iBigImage: IBigImage) {
        if (mcImageConfig.whiteList.contains(file.name) || !ImageUtil.isImage(file)) {
            return
        }
        if (removeSystemDefaultRes(file)) {
            return
        }
        if (((mcImageConfig.isCheckSize && ImageUtil.isBigSizeImage(file, mcImageConfig.maxSize))
                    || (mcImageConfig.isCheckPixels
                    && ImageUtil.isBigPixelImage(
                file,
                mcImageConfig.maxWidth,
                mcImageConfig.maxHeight
            )))
            && !mcImageConfig.bigImageWhiteList.contains(file.name)
        ) {
            iBigImage.onBigImage(file)
        }
        imageFileList.add(file)
    }

    private fun mtDispatchOptimizeTask(imageFileList: ArrayList<File>) {
        if (imageFileList.size == 0 || bigImgList.isNotEmpty()) {
            return
        }
        val coreNum = Runtime.getRuntime().availableProcessors()
        if (imageFileList.size < coreNum || !mcImageConfig.multiThread) {
            for (file in imageFileList) {
                optimizeImage(file)
            }
        } else {
            val results = ArrayList<Future<Unit>>()
            val pool = Executors.newFixedThreadPool(coreNum)
            val part = imageFileList.size / coreNum
            for (i in 0 until coreNum) {
                val from = i * part
                val to = if (i == coreNum - 1) imageFileList.size - 1 else (i + 1) * part - 1
                results.add(pool.submit(Callable<Unit> {
                    for (index in from..to) {
                        optimizeImage(imageFileList[index])
                    }
                }))
            }
            for (f in results) {
                try {
                    f.get()
                } catch (ignore: Exception) {
                }
            }
        }
    }

    private fun optimizeImage(file: File) {
        val path: String = file.path
        var oldSize = 0L
        if (File(path).exists()) {
            oldSize = File(path).length()
            oldCompressSize += oldSize
        }
        when (mcImageConfig.optimizeType) {
            Config.OPTIMIZE_WEBP_CONVERT ->
                WebpUtils.securityFormatWebp(file, mcImageConfig, mcImageProject)
            Config.OPTIMIZE_COMPRESS_PICTURE ->
                CompressUtil.compressImg(file)
        }
        val newSize = countNewSize(path)
        if (newSize == oldSize) {
            noSizeChangeResource.add(path)
        }
    }

    private fun countNewSize(path: String): Long {
        var newFileSize = 0L
        if (File(path).exists()) {
            newFileSize += File(path).length()
        } else {
            //转成了webp
            val indexOfDot = path.lastIndexOf(".")
            val webpPath = path.substring(0, indexOfDot) + ".webp"
            if (File(webpPath).exists()) {
                newFileSize += File(webpPath).length()
            } else {
                LogUtil.log("McImage: optimizeImage have some Exception!!!:$path")
            }
        }
        newCompressSize += newFileSize
        return newFileSize
    }

    private fun checkBigImage() {
        if (bigImgList.size != 0) {
            val stringBuffer = StringBuffer(
                "You have big Imgages with big size or large pixels," +
                        "please confirm whether they are necessary or whether they can to be compressed. " +
                        "If so, you can config them into bigImageWhiteList to fix this Exception!!!\n"
            )
            for (i: Int in 0 until bigImgList.size) {
                stringBuffer.append(bigImgList[i])
                stringBuffer.append("\n")
            }
            throw GradleException(stringBuffer.toString())
        }
    }


    private fun checkMcTools(project: Project) {
        if (mcImageConfig.mctoolsDir.isBlank()) {
            FileUtil.setRootDir(project.rootDir.path)
        } else {
            FileUtil.setRootDir(mcImageConfig.mctoolsDir)
        }

        if (!FileUtil.getToolsDir().exists()) {
            throw GradleException("You need put the mctools dir in project root")
        }
    }

    private fun replaceSizeInfo(): String {
        return "->>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n" +
                "before replace optimize: " + oldReplaceSize / 1024 + "KB\n" +
                "after replace optimize: " + newReplaceSize / 1024 + "KB\n" +
                "replace optimize size: " + (oldReplaceSize - newReplaceSize) / 1024 + "KB\n" +
                "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<-"
    }

    private fun compressSizeInfo(): String {
        return "->>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n" +
                "before compress optimize: " + oldCompressSize / 1024 + "KB\n" +
                "after compress optimize: " + newCompressSize / 1024 + "KB\n" +
                "compress optimize size: " + (oldCompressSize - newCompressSize) / 1024 + "KB\n" +
                "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<-"
    }

    private fun compressNoSizeChangeInfo(): String {
        if (noSizeChangeResource.isEmpty()) {
            return ""
        }
        var str = "->>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n" +
                "compress size not change:\n"
        noSizeChangeResource.map {
            str += "  ->$it\n"
        }
        str += "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<-"
        return str
    }

    private fun totalOptimizeInfo(): String {
        return "->>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n" +
                "before McImage optimize: " + (oldReplaceSize + oldCompressSize) / 1024 + "KB\n" +
                "after McImage optimize: " + (newReplaceSize + newCompressSize) / 1024 + "KB\n" +
                "McImage optimize size: " + (oldReplaceSize + oldCompressSize - newReplaceSize - newCompressSize) / 1024 + "KB\n" +
                "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<-"
    }

    override fun getName(): String {
        return "CpImage"
    }

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return TransformManager.CONTENT_CLASS
    }

    override fun isIncremental(): Boolean {
        return true
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }
}