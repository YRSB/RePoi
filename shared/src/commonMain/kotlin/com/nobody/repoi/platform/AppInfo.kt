package com.nobody.repoi.platform

expect object AppInfo {
    val versionName: String
    val versionCode: String
    val platform: String
    val brand: String
    val model: String
    val systemVersion: String
}
