package com.zhipu.herbreview

import android.app.Application
import com.zhipu.herbreview.work.PendingReviewPollWorker

class HerbReviewApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PendingReviewPollWorker.schedule(this)
    }
}
