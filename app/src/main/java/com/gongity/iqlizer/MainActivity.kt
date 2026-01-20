package com.gongity.iqlizer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    private var audioService: AudioService? = null
    private lateinit var audioManager: AudioManager
    private lateinit var sharedPrefs: SharedPreferences
    private val gson = Gson()

    private lateinit var swEnableEq: SwitchCompat
    private lateinit var spPresets: Spinner
    private lateinit var btnSavePreset: Button
    private lateinit var btnDeletePreset: Button
    private lateinit var btnReset: Button
    private val seekBars = mutableMapOf<Int, SeekBar>()

    private var presets = mutableMapOf<String, FloatArray>()
    private lateinit var presetAdapter: ArrayAdapter<String>

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioService.LocalBinder
            audioService = binder.getService()
            initUi()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            saveCurrentEqSettings()
            audioService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPrefs = getSharedPreferences("IQlizerPresets", Context.MODE_PRIVATE)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        startAudioService()
        bindToAudioService()
    }

    private fun startAudioService() {
        val serviceIntent = Intent(this, AudioService::class.java)
        startForegroundService(serviceIntent)
    }

    private fun bindToAudioService() {
        val serviceIntent = Intent(this, AudioService::class.java)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun initUi() {
        loadCurrentEqSettings()
        initSeekBars()
        setupMediaButtons()
        setupEnableSwitch()
        setupPresetSpinner()
        setupSavePresetButton()
        setupDeletePresetButton()
        setupResetButton()
        loadPresets()
    }

    private fun initSeekBars() {
        val seekBarIds = listOf(R.id.sb60Hz, R.id.sb230Hz, R.id.sb910Hz, R.id.sb4kHz, R.id.sb14kHz)
        seekBarIds.forEachIndexed { index, id ->
            val seekBar = findViewById<SeekBar>(id)
            seekBars[index] = seekBar
            seekBar.progress = AudioService.eqBands[index].toInt()
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        audioService?.updateBandLevel(index, progress)
                        // If a preset is selected, auto-update it
                        (spPresets.selectedItem as? String)?.let { presetName ->
                            savePreset(presetName, andUpdateUi = false)
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }

    private fun setupMediaButtons() {
        val btnPlayPause = findViewById<Button>(R.id.btnPlayPause)
        val btnPrevious = findViewById<Button>(R.id.btnPrevious)
        val btnNext = findViewById<Button>(R.id.btnNext)

        btnPlayPause.setOnClickListener { sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        btnPrevious.setOnClickListener { sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        btnNext.setOnClickListener { sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT) }
    }

    private fun setupEnableSwitch() {
        swEnableEq = findViewById(R.id.swEnableEq)
        swEnableEq.isChecked = AudioService.isEqEnabled
        swEnableEq.setOnCheckedChangeListener { _, isChecked ->
            audioService?.setEqEnabled(isChecked)
        }
    }

    private fun setupPresetSpinner() {
        spPresets = findViewById(R.id.spPresets)
        presetAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf())
        presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spPresets.adapter = presetAdapter

        spPresets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                parent?.getItemAtPosition(position)?.let { presetName ->
                    loadPreset(presetName as String)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                 // This can be used to reset if needed, but we do it manually
            }
        }
    }

    private fun setupSavePresetButton() {
        btnSavePreset = findViewById(R.id.btnSavePreset)
        btnSavePreset.setOnClickListener {
            showSavePresetDialog()
        }
    }

    private fun setupDeletePresetButton() {
        btnDeletePreset = findViewById(R.id.btnDeletePreset)
        btnDeletePreset.setOnClickListener {
            val selectedPreset = spPresets.selectedItem as? String
            if (selectedPreset != null) {
                showDeletePresetDialog(selectedPreset)
            }
        }
    }

    private fun setupResetButton() {
        btnReset = findViewById(R.id.btnReset)
        btnReset.setOnClickListener {
            spPresets.setSelection(AdapterView.INVALID_POSITION)
            resetEqualizer()
        }
    }

    private fun showSavePresetDialog() {
        val editText = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Save Preset")
            .setMessage("Enter a name for the preset:")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val name = editText.text.toString()
                if (name.isNotBlank()) {
                    savePreset(name, andUpdateUi = true)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeletePresetDialog(presetName: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Preset")
            .setMessage("Are you sure you want to delete '$presetName'?")
            .setPositiveButton("Delete") { _, _ ->
                deletePreset(presetName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun savePreset(name: String, andUpdateUi: Boolean) {
        val isNewPreset = !presets.containsKey(name)
        presets[name] = AudioService.eqBands.clone()
        savePresets()

        if (andUpdateUi) {
            if (isNewPreset) {
                presetAdapter.add(name)
            }
            spPresets.setSelection(presetAdapter.getPosition(name))
        }
    }

    private fun deletePreset(name: String) {
        presets.remove(name)
        savePresets()
        presetAdapter.remove(name)
        presetAdapter.notifyDataSetChanged()

        if (presetAdapter.isEmpty) {
            resetEqualizer()
        }
    }

    private fun resetEqualizer() {
        val neutralBands = FloatArray(5) { 50f }
        AudioService.eqBands = neutralBands
        audioService?.applyAllCurrentBands()
        updateSeekBars()
    }

    private fun loadPreset(name: String) {
        val bandLevels = presets[name] ?: return
        AudioService.eqBands = bandLevels.clone()
        audioService?.applyAllCurrentBands()
        updateSeekBars()
    }

    private fun savePresets() {
        val json = gson.toJson(presets)
        sharedPrefs.edit().putString("presets", json).apply()
    }

    private fun loadPresets() {
        val json = sharedPrefs.getString("presets", null)
        if (json != null) {
            val type = object : TypeToken<MutableMap<String, FloatArray>>() {}.type
            presets = gson.fromJson(json, type)
        }
        presetAdapter.clear()
        presetAdapter.addAll(presets.keys.toMutableList())
        presetAdapter.notifyDataSetChanged()
    }

    private fun saveCurrentEqSettings() {
        val json = gson.toJson(AudioService.eqBands)
        sharedPrefs.edit().putString("currentEq", json).apply()
    }

    private fun loadCurrentEqSettings() {
        val json = sharedPrefs.getString("currentEq", null)
        if (json != null) {
            val type = object : TypeToken<FloatArray>() {}.type
            AudioService.eqBands = gson.fromJson(json, type)
        }
    }

    private fun updateSeekBars() {
        seekBars.forEach { (band, seekBar) ->
            seekBar.progress = AudioService.eqBands[band].toInt()
        }
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        if (hasMediaControlPermission()) {
            val event = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            audioManager.dispatchMediaKeyEvent(event)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(upEvent)
        } else {
            requestMediaControlPermission()
        }
    }

    private fun hasMediaControlPermission(): Boolean {
        val componentName = ComponentName(this, MediaNotificationListener::class.java)
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return enabledListeners?.contains(componentName.flattenToString()) == true
    }

    private fun requestMediaControlPermission() {
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        bindToAudioService()
    }

    override fun onPause() {
        super.onPause()
        saveCurrentEqSettings()
        unbindService(connection)
    }
}