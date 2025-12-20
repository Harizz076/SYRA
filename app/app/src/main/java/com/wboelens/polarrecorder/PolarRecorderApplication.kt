package com.wboelens.polarrecorder

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.managers.RecordingManager
import com.wboelens.polarrecorder.managers.SpotifyManager
import com.wboelens.polarrecorder.managers.SurveyManager
import com.wboelens.polarrecorder.viewModels.DeviceViewModel
import com.wboelens.polarrecorder.viewModels.LogViewModel

class PolarRecorderApplication : Application() {
    companion object {
        private const val TAG = "PolarRecorderApp"

        // Shared instances
        lateinit var instance: PolarRecorderApplication
            private set

        lateinit var deviceViewModel: DeviceViewModel
            private set

        lateinit var logViewModel: LogViewModel
            private set

        lateinit var preferencesManager: PreferencesManager
            private set

        lateinit var dataSavers: DataSavers
            private set

        @SuppressLint("StaticFieldLeak") // Using Application context, not Activity context
        lateinit var polarManager: PolarManager
            private set

        @SuppressLint("StaticFieldLeak") // Using Application context, not Activity context
        lateinit var spotifyManager: SpotifyManager
            private set

        @SuppressLint("StaticFieldLeak") // Using Application context, not Activity context
        lateinit var surveyManager: SurveyManager
            private set

        @SuppressLint("StaticFieldLeak") // Using Application context, not Activity context
        lateinit var recordingManager: RecordingManager
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d(TAG, "Application onCreate - initializing shared managers")

        // Initialize ViewModels
        deviceViewModel = DeviceViewModel()
        logViewModel = LogViewModel()

        // Initialize managers
        preferencesManager = PreferencesManager(applicationContext)
        dataSavers = DataSavers(applicationContext, logViewModel, preferencesManager)
        spotifyManager = SpotifyManager(applicationContext, dataSavers)
        surveyManager = SurveyManager(applicationContext, logViewModel)
        polarManager = PolarManager(applicationContext, deviceViewModel, logViewModel, preferencesManager)
        recordingManager = RecordingManager(
            applicationContext,
            polarManager,
            logViewModel,
            deviceViewModel,
            preferencesManager,
            dataSavers,
            spotifyManager
        )

        Log.d(TAG, "Shared managers initialized successfully")
    }
}

