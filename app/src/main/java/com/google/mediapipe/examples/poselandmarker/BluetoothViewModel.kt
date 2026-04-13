package com.google.mediapipe.examples.poselandmarker

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class BluetoothViewModel(application: Application) : AndroidViewModel(application) {
    val bluetoothManager = BluetoothManager(application.applicationContext)

    override fun onCleared() {
        super.onCleared()
        bluetoothManager.disconnect()
    }
}