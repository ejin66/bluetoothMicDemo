package com.ejin.micbluetooth

import android.Manifest
import android.annotation.TargetApi
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    val TAG = "MainActivity"
    private var mAdapter: BluetoothAdapter? = null
    private var mReceiver: BluetoothBroadcastReceiver? = null
    private var mBluetoothA2DP: BluetoothA2dp? = null
    private val BLUETOOTH_REQUEST_ENABLE_CODE = 1000
    private val mHandler = Handler()
    private var isAudioPlay = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
        oldApi.setOnClickListener {
            initBluetooth()
        }

        newApi.setOnClickListener {
            initBluetooth2()
        }

        startAudio.setOnClickListener {
            audio()
        }

        stopAudio.setOnClickListener {
            stopAudio()
        }
    }

    private fun audio() {
        if (isAudioPlay) return

        val frequency = 44100
        val channelConfiguration = AudioFormat.CHANNEL_IN_MONO
        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

        //录音缓存大小
        val mRecBuffSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding)
        Log.d(TAG, "record buffer: $mRecBuffSize")
        val mAudioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration, audioEncoding, mRecBuffSize)

        val mPlayBuffSize = AudioTrack.getMinBufferSize(frequency, AudioFormat.CHANNEL_OUT_MONO, audioEncoding)
        Log.d(TAG, "play buffer: $mPlayBuffSize")
        val mAudioTrack = AudioTrack(AudioManager.STREAM_MUSIC, frequency, AudioFormat.CHANNEL_OUT_MONO, audioEncoding, mPlayBuffSize, AudioTrack.MODE_STREAM)

        Thread {
            val dataBuffer = ByteArray(mRecBuffSize)
            mAudioRecord.startRecording()
            mAudioTrack.play()
            isAudioPlay = true
            var size: Int
            Log.d(TAG, "audio start")
            while (isAudioPlay) {
                size = mAudioRecord.read(dataBuffer, 0, mRecBuffSize)
                Log.d(TAG, "audio record size: $size")
                mAudioTrack.write(dataBuffer, 0, size)
            }
            mAudioRecord.stop()
            mAudioTrack.stop()
            Log.d(TAG, "audio stop")
        }.start()
    }

    private fun stopAudio() {
        isAudioPlay = false
    }

    private fun initBluetooth() {
        mAdapter = BluetoothAdapter.getDefaultAdapter().apply {
            if (!isEnabled) {
                enable()
//                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//                startActivityForResult()
            }
        }

        if (mReceiver == null) {
            mReceiver = BluetoothBroadcastReceiver().apply {
                val intentFilter = IntentFilter().apply {
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                    addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                }
                registerReceiver(this, intentFilter)
            }
        }


        mAdapter?.startDiscovery()
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private fun initBluetooth2() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mAdapter = bluetoothManager.adapter

        if (mAdapter == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_SHORT).show()
            return
        }

        if (!mAdapter!!.isEnabled) {
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), BLUETOOTH_REQUEST_ENABLE_CODE)
            return
        }

        Log.d(TAG, "start scan bluetooth")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            val callback = BluetoothAdapter.LeScanCallback { device, rssi, scanRecord ->
                Log.d(TAG, "${device.name}  ${device.address}")
            }
            mHandler.postDelayed({
                Log.d(TAG, "stop2 scan bluetooth")
                mAdapter!!.stopLeScan(callback)
            }, 8000)
            mAdapter!!.startLeScan(callback)
        } else {
            mAdapter!!.bluetoothLeScanner.apply {
                val callback = object: ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult?) {
                        result?.device?.apply {
                            Log.d(TAG, "$name  $address")
                        }
                    }
                }
                mHandler.postDelayed({
                    Log.d(TAG, "stop3 scan bluetooth")
                    stopScan(callback)
                }, 8000)
                startScan(callback)
            }
        }

    }

    private fun checkDevice(device: BluetoothDevice) {
        if (device.name != "Le Sports BT") {
            return
        }
        Log.d(TAG, "find le sports")

        when(device.bondState) {

            BluetoothDevice.BOND_NONE -> {
                Log.d(TAG, "create bond")
                val createBondMethod = (BluetoothDevice::class.java).getMethod("createBond")
                createBondMethod.isAccessible = true
                createBondMethod.invoke(device)
            }
            BluetoothDevice.BOND_BONDED -> {
                connect(device)
            }

        }
    }

    private fun connect(device: BluetoothDevice) {
        getBluetoothA2DP {
            if (mBluetoothA2DP == null || mAdapter == null) {
                Log.d(TAG, "mBluetoothA2DP or mAdapter is null")
                return@getBluetoothA2DP
            }

            Log.d(TAG, "connect to device")
            (mBluetoothA2DP!!::class.java).getDeclaredMethod("connect", BluetoothDevice::class.java).apply {
                isAccessible = true
                invoke(mBluetoothA2DP, device)
            }
        }
    }

    private fun getBluetoothA2DP(callback: ()-> Unit) {
        if (mAdapter == null) return

        if (mBluetoothA2DP != null) return

        mAdapter!!.getProfileProxy(this, object: BluetoothProfile.ServiceListener {
            override fun onServiceDisconnected(profile: Int) {
                Log.d(TAG, "get bluetootha2dp failed")
            }

            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                Log.d(TAG, "get bluetootha2dp success")
                mBluetoothA2DP = proxy as BluetoothA2dp
                callback.invoke()
            }
        }, BluetoothProfile.A2DP)
    }

    override fun onDestroy() {
        super.onDestroy()
        mReceiver?.let {
            unregisterReceiver(it)
        }
    }

    inner class BluetoothBroadcastReceiver : BroadcastReceiver() {



        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action

            when (action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> Log.d(TAG, "start discover bluetooth device")
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    Log.d(TAG, "${device.name}  ${device.address}")
                    checkDevice(device)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> Log.d(TAG, "discover bluetooth device finished")
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    when(device.bondState) {
                        BluetoothDevice.BOND_NONE -> {
                            Log.d(TAG, "bond failed")
                        }
                        BluetoothDevice.BOND_BONDED -> {
                            connect(device)
                        }
                    }
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {

                    val subAction = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1)
                    when(subAction) {
                        BluetoothA2dp.STATE_CONNECTED -> {
                            Log.d(TAG, "connected to device")
                            audio()
                        }
                        BluetoothA2dp.STATE_DISCONNECTED -> {
                            Log.d(TAG, "disconnected from device")
                        }
                    }

                }
            }
        }
    }

}
