package com.academicmorning.app

import android.app.Application

/**
 * 学术晨报 Application。手工 DI 入口：
 * 全局通过 (context.applicationContext as AcademicMorningApp).container 访问所有仓库。
 */
class AcademicMorningApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.init()
    }
}
