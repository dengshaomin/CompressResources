# CompressResources
check and compress resource file when build project
在根目录build.gradle中添加
repositories {
        maven{
            url uri('https://jitpack.io')
        }
}
dependencies {
        classpath 'com.github.dengshaomin:CompressResources:1.0.7'
    }

在根目录增加cpimage.gradle
apply plugin: 'CpImage'
McImageConfig {
    isCheckSize true //Whether to detect image size，default true
    optimizeType "Compress"
    //Optimize Type，"ConvertWebp" or "Compress"，default "Compress", "CompressWebp" is a better compression ratio but it don't support api < 18
    maxSize 1 * 1024 * 1024 //big image size threshold，default 1MB
    enableWhenDebug true //switch in debug build，default true
    isCheckPixels true // Whether to detect image pixels of width and height，default true
    maxWidth 1000 //default 1000
    maxHeight 1000 //default 1000
    whiteList = [ //do not do any optimization for the images who in the list
                  "icon_launcher.png"
    ]
    mctoolsDir "$rootDir"
    isSupportAlphaWebp false
    //Whether support convert the Image with Alpha chanel to Webp，default false, the images with alpha chanels will be compressed.if config true, its need api level >=18 or do some compatible measures
    multiThread true  //Whether open multi-thread processing，default true
    bigImageWhiteList = [
    ] //do not detect big size or large pixels for the images who in the list
}
在app目录下的build.gradle中添加
apply from: '../cpimage.gradle'
复制项目中的mctools(开源压缩算法脚本)文件夹到根目录
