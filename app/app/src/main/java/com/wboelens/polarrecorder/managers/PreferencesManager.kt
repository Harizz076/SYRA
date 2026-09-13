package com.wboelens.polarrecorder.managers

import android.content.Context
import android.content.SharedPreferences
import com.wboelens.polarrecorder.dataSavers.FileSystemDataSaverConfig
import com.wboelens.polarrecorder.dataSavers.MQTTConfig

data class PreferenceConfig<T>(val key: String, val defaultValue: T)

object Preferences {
  val MQTT_HOST = PreferenceConfig("mqtt_broker_host", "")
  val MQTT_PORT = PreferenceConfig("mqtt_broker_port", 1883)
  val MQTT_USE_SSL = PreferenceConfig("mqtt_broker_use_ssl", false)
  val MQTT_USERNAME = PreferenceConfig("mqtt_username", "")
  val MQTT_PASSWORD = PreferenceConfig("mqtt_password", "")
  val MQTT_CLIENT_ID = PreferenceConfig("mqtt_client_id", "")
  val MQTT_TOPIC_PREFIX = PreferenceConfig("mqtt_topic_prefix", "")
  val MQTT_ENABLED = PreferenceConfig("mqtt_enabled", false)

  val FILE_SYSTEM_BASE_DIRECTORY = PreferenceConfig("file_system_base_directory", "")
  @Suppress("MagicNumber")
  val FILE_SYSTEM_RECORDING_SPLIT_AT_SIZE_MB =
      PreferenceConfig("file_system_recording_split_at_size_mb", 20)
  val FILE_SYSTEM_ENABLED = PreferenceConfig("file_system_enabled", false)

  val MEDIA_TRACK_ENABLED = PreferenceConfig("media_track_enabled", true)

  val RECORDING_NAME = PreferenceConfig("recording_name", "syra")
  val RECORDING_NAME_APPEND_TIMESTAMP = PreferenceConfig("recording_name_append_timestamp", true)
  val RECORDING_STOP_ON_DISCONNECT = PreferenceConfig("recording_stop_on_disconnect", false)
  val AUTO_RECORDING_ENABLED = PreferenceConfig("auto_recording_enabled", true)

  val PAIRED_DEVICE_IDS = PreferenceConfig("paired_device_ids", "")
  val AUTO_CONNECT_DEVICE_ID = PreferenceConfig("auto_connect_device_id", "")

  // ── Onboarding (local-only, never transmitted) ──────────────────────────
  val ONBOARDING_COMPLETE = PreferenceConfig("onboarding_complete", false)
  val PARTICIPANT_ID = PreferenceConfig("participant_id", "")
  /** Version string of the ethical statement the participant accepted. */
  val CONSENT_VERSION = PreferenceConfig("consent_version", "")

  // ── Study Design ───────────────────────────────────────────────────────────────
  /**
   * Controls the active study protocol:
   *  TRIGGERED         – music-playback-triggered pre/post EMA only; no BLE, no random probes
   *  TRIGGERED_RANDOM  – triggered EMA + usage-optimal random ESM probes; no BLE
   *  RANDOM_ESM        – usage-optimal random ESM probes only; media listeners disabled; no BLE
   *  PHYSIOLOGY        – triggered EMA + random ESM + Polar H10 BLE telemetry
   */
  val STUDY_MODE = PreferenceConfig("study_mode", "TRIGGERED")
}

class PreferencesManager(context: Context) {
  companion object {
    private var PREF_NAME = "com.wboelens.polarrecorder.PREFS"
  }

  private val mPref: SharedPreferences =
      context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

  var mqttConfig: MQTTConfig
    get() {
      return MQTTConfig(
          host = mPref.getString(Preferences.MQTT_HOST.key, Preferences.MQTT_HOST.defaultValue)!!,
          port = mPref.getInt(Preferences.MQTT_PORT.key, Preferences.MQTT_PORT.defaultValue),
          useSSL =
              mPref.getBoolean(Preferences.MQTT_USE_SSL.key, Preferences.MQTT_USE_SSL.defaultValue),
          username =
              mPref.getString(
                  Preferences.MQTT_USERNAME.key,
                  Preferences.MQTT_USERNAME.defaultValue,
              )!!,
          password =
              mPref.getString(
                  Preferences.MQTT_PASSWORD.key,
                  Preferences.MQTT_PASSWORD.defaultValue,
              )!!,
          clientId =
              mPref.getString(
                  Preferences.MQTT_CLIENT_ID.key,
                  Preferences.MQTT_CLIENT_ID.defaultValue,
              )!!,
          topicPrefix =
              mPref.getString(
                  Preferences.MQTT_TOPIC_PREFIX.key,
                  Preferences.MQTT_TOPIC_PREFIX.defaultValue,
              )!!,
      )
    }
    set(config) {
      mPref.edit().apply {
        putString(Preferences.MQTT_HOST.key, config.host)
        putInt(Preferences.MQTT_PORT.key, config.port)
        putBoolean(Preferences.MQTT_USE_SSL.key, config.useSSL)
        putString(Preferences.MQTT_USERNAME.key, config.username)
        putString(Preferences.MQTT_PASSWORD.key, config.password)
        putString(Preferences.MQTT_TOPIC_PREFIX.key, config.topicPrefix)
        putString(Preferences.MQTT_CLIENT_ID.key, config.clientId)
        apply()
      }
    }

  var mqttEnabled: Boolean
    get() = mPref.getBoolean(Preferences.MQTT_ENABLED.key, Preferences.MQTT_ENABLED.defaultValue)
    set(enabled) {
      mPref.edit().putBoolean(Preferences.MQTT_ENABLED.key, enabled).apply()
    }

  var fileSystemDataSaverConfig: FileSystemDataSaverConfig
    get() {
      return FileSystemDataSaverConfig(
          baseDirectory =
              mPref.getString(
                  Preferences.FILE_SYSTEM_BASE_DIRECTORY.key,
                  Preferences.FILE_SYSTEM_BASE_DIRECTORY.defaultValue,
              )!!,
          splitAtSizeMb =
              mPref.getInt(
                  Preferences.FILE_SYSTEM_RECORDING_SPLIT_AT_SIZE_MB.key,
                  Preferences.FILE_SYSTEM_RECORDING_SPLIT_AT_SIZE_MB.defaultValue,
              ),
      )
    }
    set(config) {
      mPref.edit().apply {
        putString(Preferences.FILE_SYSTEM_BASE_DIRECTORY.key, config.baseDirectory)
        putInt(Preferences.FILE_SYSTEM_RECORDING_SPLIT_AT_SIZE_MB.key, config.splitAtSizeMb)
        apply()
      }
    }

  var fileSystemEnabled: Boolean
    get() =
        mPref.getBoolean(
            Preferences.FILE_SYSTEM_ENABLED.key,
            Preferences.FILE_SYSTEM_ENABLED.defaultValue,
        )
    set(enabled) {
      mPref.edit().putBoolean(Preferences.FILE_SYSTEM_ENABLED.key, enabled).apply()
    }

  var mediaTrackEnabled: Boolean
    get() =
        mPref.getBoolean(
            Preferences.MEDIA_TRACK_ENABLED.key,
            Preferences.MEDIA_TRACK_ENABLED.defaultValue,
        )
    set(enabled) {
      mPref.edit().putBoolean(Preferences.MEDIA_TRACK_ENABLED.key, enabled).apply()
    }

  var recordingName: String
    get() =
        mPref.getString(Preferences.RECORDING_NAME.key, Preferences.RECORDING_NAME.defaultValue)!!
    set(name) {
      mPref.edit().putString(Preferences.RECORDING_NAME.key, name).apply()
    }

  var recordingNameAppendTimestamp: Boolean
    get() =
        mPref.getBoolean(
            Preferences.RECORDING_NAME_APPEND_TIMESTAMP.key,
            Preferences.RECORDING_NAME_APPEND_TIMESTAMP.defaultValue,
        )
    set(enabled) {
      mPref.edit().putBoolean(Preferences.RECORDING_NAME_APPEND_TIMESTAMP.key, enabled).apply()
    }

  var recordingStopOnDisconnect: Boolean
    get() =
        mPref.getBoolean(
            Preferences.RECORDING_STOP_ON_DISCONNECT.key,
            Preferences.RECORDING_STOP_ON_DISCONNECT.defaultValue,
        )
    set(name) {
      mPref.edit().putBoolean(Preferences.RECORDING_STOP_ON_DISCONNECT.key, name).apply()
    }

  var autoRecordingEnabled: Boolean
    get() =
        mPref.getBoolean(
            Preferences.AUTO_RECORDING_ENABLED.key,
            Preferences.AUTO_RECORDING_ENABLED.defaultValue,
        )
    set(enabled) {
      mPref.edit().putBoolean(Preferences.AUTO_RECORDING_ENABLED.key, enabled).apply()
    }

  var pairedDeviceIds: Set<String>
    get() {
      val idsString = mPref.getString(
          Preferences.PAIRED_DEVICE_IDS.key,
          Preferences.PAIRED_DEVICE_IDS.defaultValue
      ) ?: ""
      return if (idsString.isEmpty()) emptySet() else idsString.split(",").toSet()
    }
    set(ids) {
      val idsString = ids.joinToString(",")
      mPref.edit().putString(Preferences.PAIRED_DEVICE_IDS.key, idsString).apply()
    }

  var autoConnectDeviceId: String
    get() =
        mPref.getString(
            Preferences.AUTO_CONNECT_DEVICE_ID.key,
            Preferences.AUTO_CONNECT_DEVICE_ID.defaultValue,
        ) ?: ""
    set(deviceId) {
      mPref.edit().putString(Preferences.AUTO_CONNECT_DEVICE_ID.key, deviceId).apply()
    }

  // ── Onboarding ────────────────────────────────────────────────────────────

  /** True once the participant has accepted the ethical statement and entered their ID. */
  var onboardingComplete: Boolean
    get() =
        mPref.getBoolean(
            Preferences.ONBOARDING_COMPLETE.key,
            Preferences.ONBOARDING_COMPLETE.defaultValue,
        )
    set(complete) {
      mPref.edit().putBoolean(Preferences.ONBOARDING_COMPLETE.key, complete).apply()
    }

  /** Research participant identifier provided by the researcher on first launch. */
  var participantId: String
    get() =
        mPref.getString(
            Preferences.PARTICIPANT_ID.key,
            Preferences.PARTICIPANT_ID.defaultValue,
        ) ?: ""
    set(id) {
      mPref.edit().putString(Preferences.PARTICIPANT_ID.key, id).apply()
    }

  /** Version of the ethical consent statement the participant accepted (e.g. "ETH-2026-V2"). */
  var consentVersion: String
    get() =
        mPref.getString(
            Preferences.CONSENT_VERSION.key,
            Preferences.CONSENT_VERSION.defaultValue,
        ) ?: ""
    set(version) {
      mPref.edit().putString(Preferences.CONSENT_VERSION.key, version).apply()
    }

  /**
   * Atomically records consent acceptance and participant ID in a single SharedPreferences
   * transaction — used by the onboarding flow to prevent partial writes.
   */
  fun completeOnboarding(participantId: String, consentVersion: String) {
    mPref.edit().apply {
      putString(Preferences.PARTICIPANT_ID.key, participantId)
      putString(Preferences.CONSENT_VERSION.key, consentVersion)
      putBoolean(Preferences.ONBOARDING_COMPLETE.key, true)
      apply()
    }
  }

  // ── Study Design ──────────────────────────────────────────────────────────────

  /**
   * The active study protocol mode.
   * One of: TRIGGERED | TRIGGERED_RANDOM | RANDOM_ESM | PHYSIOLOGY.
   * Only PHYSIOLOGY mode requires a connected Polar sensor and enables BLE data collection.
   */
  var studyMode: String
    get() =
        mPref.getString(
            Preferences.STUDY_MODE.key,
            Preferences.STUDY_MODE.defaultValue,
        ) ?: Preferences.STUDY_MODE.defaultValue
    set(mode) {
      mPref.edit().putString(Preferences.STUDY_MODE.key, mode).apply()
    }

  /** Convenience: true only when study mode is PHYSIOLOGY and a Polar sensor is mandatory. */
  val requiresSensor: Boolean
    get() = studyMode == "PHYSIOLOGY"

  /**
   * Convenience: true when the study mode involves media-playback-triggered surveys.
   * Returns false for RANDOM_ESM, which disables all media playback listeners.
   */
  val requiresMediaListener: Boolean
    get() = studyMode != "RANDOM_ESM"

  /**
   * Convenience: true when the study mode schedules random/optimal ESM probes.
   * Returns true for TRIGGERED_RANDOM, RANDOM_ESM, and PHYSIOLOGY.
   */
  val requiresEsmScheduler: Boolean
    get() = studyMode in setOf("TRIGGERED_RANDOM", "RANDOM_ESM", "PHYSIOLOGY")
}
